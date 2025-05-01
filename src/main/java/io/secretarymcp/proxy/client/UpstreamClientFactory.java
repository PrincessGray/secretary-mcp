package io.secretarymcp.proxy.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.secretarymcp.model.ConnectionProfile;
import io.secretarymcp.model.SseConfig;
import io.secretarymcp.model.StdioConfig;
import io.secretarymcp.util.Constants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * 上游客户端工厂，负责创建、缓存和管理不同类型的上游客户端
 */
@RequiredArgsConstructor
public class UpstreamClientFactory {
    private static final Logger log = LoggerFactory.getLogger(UpstreamClientFactory.class);
    
    private final ObjectMapper objectMapper;
    
    // 客户端缓存，按任务ID索引
    private final ConcurrentHashMap<String, UpstreamClient> clientCache = new ConcurrentHashMap<>();
    
    // 是否正在关闭
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    /**
     * 创建或获取上游客户端
     * 如果已有缓存的客户端，则优先使用缓存，避免重复创建
     */
    public Mono<UpstreamClient> getOrCreateClient(UpstreamClientConfig config) {
        // 检查是否正在关闭
        if (isShuttingDown.get()) {
            return Mono.error(new IllegalStateException("工厂正在关闭，无法创建新客户端"));
        }
        
        // 检查客户端缓存
        String taskId = config.getTaskId();
        UpstreamClient cachedClient = clientCache.get(taskId);
        
        if (cachedClient != null && cachedClient.isInitialized()) {
            log.debug("使用缓存的上游客户端: {}", taskId);
            
            // 先进行健康检查
            return cachedClient.ping()
                    .flatMap(isHealthy -> {
                        if (isHealthy) {
                            return Mono.just(cachedClient);
                        } else {
                            log.info("缓存的客户端不健康，重新创建: {}", taskId);
                            return createAndCacheClient(config);
                        }
                    })
                    .onErrorResume(e -> {
                        log.info("健康检查失败，重新创建客户端: {} (原因: {})", taskId, e.getMessage());
                        return createAndCacheClient(config);
                    });
        }
        
        // 创建新客户端
        return createAndCacheClient(config);
    }
    
    /**
     * 创建并缓存上游客户端
     */
    private Mono<UpstreamClient> createAndCacheClient(UpstreamClientConfig config) {
        String taskId = config.getTaskId();
        
        // 如果已有相同ID的客户端，先关闭它
        UpstreamClient existingClient = clientCache.get(taskId);
        Mono<Void> closeExisting = existingClient != null ? 
                existingClient.close().onErrorResume(e -> Mono.empty()) : 
                Mono.empty();
        
        return closeExisting
                .then(createClient(config))
                .doOnSuccess(client -> {
                    // 缓存新客户端
                    clientCache.put(taskId, client);
                    log.info("客户端已缓存: {}", taskId);
                });
    }
    
    /**
     * 创建上游客户端（核心创建逻辑）
     */
    private Mono<UpstreamClient> createClient(UpstreamClientConfig config) {
        ConnectionProfile connectionProfile = config.getConnectionProfile();
        log.info("创建新上游客户端: {} (类型: {})", 
                config.getTaskId(), connectionProfile.getConnectionType());
        
        // 创建传输层
        return createTransport(config)
                .flatMap(transport -> {
                    // 创建MCP客户端
                    McpAsyncClient client = createMcpClient(config, transport);
                    
                    // 初始化客户端
                    return client.initialize()
                            .timeout(Duration.ofSeconds(connectionProfile.getConnectionTimeoutSeconds()))
                            .doOnSuccess(result -> {
                                log.info("上游客户端初始化成功: {} -> {}", 
                                        config.getTaskId(), client.getServerInfo().name());
                                log.debug("服务器版本: {}", client.getServerInfo().version());
                                log.debug("协议版本: {}", result.protocolVersion());
                            })
                            .thenReturn(new UpstreamClient(config.getTaskId(), config.getTaskName(), client, true, connectionProfile.getConnectionType()));
                })
                .onErrorResume(e -> {
                    log.error("创建上游客户端失败: {} (原因: {})", config.getTaskId(), e.getMessage());
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 创建传输层
     */
    private Mono<McpClientTransport> createTransport(UpstreamClientConfig config) {
        ConnectionProfile connectionProfile = config.getConnectionProfile();
        if (connectionProfile.getConnectionType() == Constants.ConnectionType.STDIO) {
            return createStdioTransport(config);
        } else if (connectionProfile.getConnectionType() == Constants.ConnectionType.SSE) {
            return createSseTransport(config);
        } else {
            return Mono.error(new IllegalArgumentException(
                    "不支持的连接类型: " + connectionProfile.getConnectionType()));
        }
    }
    
    /**
     * 创建STDIO传输层
     */
    private Mono<McpClientTransport> createStdioTransport(UpstreamClientConfig config) {
        ConnectionProfile connectionProfile = config.getConnectionProfile();
        StdioConfig stdioConfig = connectionProfile.getStdioConfig();
        
        if (stdioConfig == null || stdioConfig.getCommand() == null || stdioConfig.getCommand().isEmpty()) {
            return Mono.error(new IllegalArgumentException("STDIO连接必须提供command"));
        }
        
        String originalCommand = stdioConfig.getCommand();
        List<String> originalArgs = stdioConfig.getCommandArgs() != null ? 
                                  new ArrayList<>(stdioConfig.getCommandArgs()) : 
                                  new ArrayList<>();
        
        // Windows系统特殊处理，使用cmd /c执行命令
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String command;
        List<String> args = new ArrayList<>();
        
        if (isWindows) {
            log.info("Windows系统，将使用cmd /c执行命令: {} {}", originalCommand, 
                    originalArgs.isEmpty() ? "" : String.join(" ", originalArgs));
            command = "cmd";
            args.add("/c");
            args.add(originalCommand);
            args.addAll(originalArgs);
        } else {
            command = originalCommand;
            args = originalArgs;
        }
        
        // 处理npx命令特殊情况
        boolean isNpxCommand = command.toLowerCase().contains("npx") || 
                              args.stream().anyMatch(arg -> arg.toLowerCase().contains("npx"));
                              
        if (isNpxCommand) {
            log.info("检测到npx命令，添加--quiet参数减少非JSON输出");
            
            // 如果是通过cmd调用的npx
            if (command.equalsIgnoreCase("cmd") && args.contains("/c")) {
                // 找到npx所在的参数位置
                int npxIndex = -1;
                for (int i = 0; i < args.size(); i++) {
                    if (args.get(i).toLowerCase().contains("npx")) {
                        npxIndex = i;
                        break;
                    }
                }
                
                if (npxIndex >= 0 && npxIndex < args.size() - 1) {
                    // 在npx后面直接插入--quiet参数
                    args.add(npxIndex + 1, "--quiet");
                }
            }
            // 如果直接调用npx
            else if (command.toLowerCase().contains("npx")) {
                // 在参数开头添加--quiet
                args.addFirst("--quiet");
            }
            
            log.info("修改后的命令: {} {}", command, 
                    args.isEmpty() ? "" : String.join(" ", args));
        }
        
        log.info("创建STDIO传输层: {} -> {} {}", config.getTaskId(), command, 
                args.isEmpty() ? "" : String.join(" ", args));
        
        // 记录环境变量
        log.info("系统环境变量PATH={}", System.getenv("PATH"));
        
        // 保存最终命令和参数，用于lambda表达式
        final String finalCommand = command;
        final List<String> finalArgs = args;
        
        return Mono.fromCallable(() -> {
            log.info("开始构建STDIO传输层参数: {} -> {} {}", 
                    config.getTaskId(), finalCommand, 
                    finalArgs.isEmpty() ? "" : String.join(" ", finalArgs));
            
            try {
                // 创建服务器参数构建器
                ServerParameters.Builder paramsBuilder = ServerParameters.builder(finalCommand);
                
                // 添加命令行参数
                if (!finalArgs.isEmpty()) {
                    log.info("命令行参数: {}", String.join(" ", finalArgs));
                    paramsBuilder.args(finalArgs);
                }
                
                // 添加环境变量
                Map<String, String> envVars = new HashMap<>();
                
                // 复制原有环境变量
                if (stdioConfig.getEnvironmentVars() != null && !stdioConfig.getEnvironmentVars().isEmpty()) {
                    envVars.putAll(stdioConfig.getEnvironmentVars());
                }
                
                // 确保包含PATH环境变量
                if (!envVars.containsKey("PATH") && System.getenv("PATH") != null) {
                    envVars.put("PATH", System.getenv("PATH"));
                }
                
                // 确保包含APPDATA环境变量(Windows特有)
                if (isWindows && !envVars.containsKey("APPDATA") && System.getenv("APPDATA") != null) {
                    envVars.put("APPDATA", System.getenv("APPDATA"));
                }
                
                if (!envVars.isEmpty()) {
                    log.info("环境变量: {}", envVars);
                    paramsBuilder.env(envVars);
                }
                
                // 通过环境变量传递工作目录
                String workingDir = stdioConfig.getWorkingDir();
                if (workingDir == null || workingDir.isEmpty()) {
                    // 如果未指定工作目录，使用当前目录
                    workingDir = System.getProperty("user.dir");
                    log.info("未指定工作目录，使用当前目录: {}", workingDir);
                }
                
                log.info("工作目录: {}", workingDir);
                paramsBuilder.addEnvVar("WORKING_DIR", workingDir);
                
                // 构建服务器参数
                ServerParameters params = paramsBuilder.build();
                log.info("STDIO传输层参数构建完成: {}", config.getTaskId());
                
                // 创建并返回传输层
                return (McpClientTransport) new StdioClientTransport(params, objectMapper);
            } catch (Exception e) {
                log.error("创建STDIO传输层失败，详细错误: ", e);
                throw e;
            }
        })
        .doOnSuccess(transport -> log.info("STDIO传输层创建成功: {}", config.getTaskId()))
        .doOnError(e -> log.error("创建STDIO传输层失败: {} (详细错误: {})", config.getTaskId(), e.getMessage(), e));
    }
    
    /**
     * 创建SSE传输层
     */
    private Mono<McpClientTransport> createSseTransport(UpstreamClientConfig config) {
        ConnectionProfile connectionProfile = config.getConnectionProfile();
        SseConfig sseConfig = connectionProfile.getSseConfig();
        
        if (sseConfig == null || sseConfig.getServerUrl() == null || sseConfig.getServerUrl().isEmpty()) {
            return Mono.error(new IllegalArgumentException("SSE连接必须提供serverUrl"));
        }
        
        return Mono.fromCallable(() -> {
            String serverUrl = sseConfig.getServerUrl();
            
            // 检查URL是否已包含/sse
            boolean containsSsePath = serverUrl.contains("/sse");
            
            // 如果URL已包含/sse，则移除/sse部分以避免重复
            String baseUrl;
            if (containsSsePath) {
                // 简单替换方法，移除/sse，保留查询参数
                baseUrl = serverUrl.replace("/sse", "");
                log.info("检测到URL已包含/sse，已移除: {}", baseUrl);
            } else {
                baseUrl = serverUrl;
            }
            
            log.info("创建SSE传输层: {} -> {}", config.getTaskId(), baseUrl);
            
            // 创建WebClient构建器
            WebClient.Builder webClientBuilder = WebClient.builder()
                    .baseUrl(baseUrl);
            

            
            // 添加认证Token
            if (sseConfig.getBearerToken() != null && !sseConfig.getBearerToken().isEmpty()) {
                webClientBuilder.defaultHeader("Authorization", "Bearer " + sseConfig.getBearerToken());
            }
            
            // 使用builder创建SSE传输层，设置重试策略
            WebFluxSseClientTransport.Builder transportBuilder = WebFluxSseClientTransport.builder(webClientBuilder)
                    .sseEndpoint("/sse");  // 使用默认端点
                
            // 创建并返回传输层
            return (McpClientTransport) transportBuilder.build();
        })
        .doOnSuccess(transport -> log.info("SSE传输层创建成功: {}", config.getTaskId()))
        .doOnError(e -> log.error("创建SSE传输层失败: {} (详细错误: {})", config.getTaskId(), e.getMessage(), e));
    }
        
    /**
     * 创建MCP客户端
     */
    private McpAsyncClient createMcpClient(UpstreamClientConfig config, McpClientTransport transport) {
        ConnectionProfile connectionProfile = config.getConnectionProfile();
        
        // 构建客户端能力
        McpSchema.ClientCapabilities capabilities = null;
        boolean enableRoots = false;
        boolean enableSampling = false;
        
        if (connectionProfile.getGeneralConfig() != null) {
            if (connectionProfile.getGeneralConfig().getEnableRoots() != null) {
                enableRoots = connectionProfile.getGeneralConfig().getEnableRoots();
            }
            
            if (connectionProfile.getGeneralConfig().getEnableSampling() != null) {
                enableSampling = connectionProfile.getGeneralConfig().getEnableSampling();
            }
        }
        
        if (enableRoots || enableSampling) {
            var capabilitiesBuilder = McpSchema.ClientCapabilities.builder();
            
            if (enableRoots) {
                capabilitiesBuilder.roots(true);  // 启用文件系统根支持，带列表变更通知
            }
            
            if (enableSampling) {
                capabilitiesBuilder.sampling();   // 启用LLM采样支持
            }
            
            capabilities = capabilitiesBuilder.build();
        }
        
        // 创建异步客户端
        var builder = McpClient.async(transport)
                .requestTimeout(Duration.ofSeconds(connectionProfile.getConnectionTimeoutSeconds()));
        
        // 设置能力（如果已配置）
        if (capabilities != null) {
            builder.capabilities(capabilities);
        }
        
        // 配置采样处理（如果启用）
        if (enableSampling && config.getSamplingHandler() != null) {
            builder.sampling(config.getSamplingHandler());
        }
        
        // 配置工具变更消费者
        builder.toolsChangeConsumer(tools -> Mono.fromRunnable(() -> {
            log.info("工具已更新，任务ID: {}，工具数量: {}", config.getTaskId(), tools.size());
            if (log.isDebugEnabled()) {
                tools.forEach(tool -> log.debug("  - 工具: {}", tool.name()));
            }
        }));

        // 配置资源变更消费者
        builder.resourcesChangeConsumer(resources ->
                Mono.fromRunnable(() -> log.info("资源已更新，任务ID: {}，资源数量: {}",
                        config.getTaskId(), resources.size()))
        );

        // 配置提示变更消费者
        builder.promptsChangeConsumer(prompts ->
                Mono.fromRunnable(() -> log.info("提示已更新，任务ID: {}，提示数量: {}",
                        config.getTaskId(), prompts.size()))
        );
        
        // 记录SSE连接特殊处理
        if (connectionProfile.getConnectionType() == Constants.ConnectionType.SSE) {
            log.info("为SSE连接配置心跳机制，在客户端初始化后自动启动: {}", config.getTaskId());
        }
        
        // 构建客户端
        return builder.build();
    }

    /**
     * 获取已缓存的客户端列表
     */
    public Mono<Map<String, Boolean>> getCachedClients() {
        return Mono.fromCallable(() -> {
            Map<String, Boolean> result = new ConcurrentHashMap<>();
            clientCache.forEach((id, client) -> result.put(id, client.isInitialized()));
            return result;
        });
    }
    
    /**
     * 关闭指定的客户端
     */
    public Mono<Boolean> closeClient(String taskId) {
        UpstreamClient client = clientCache.get(taskId);
        if (client == null) {
            return Mono.just(true);
        }
        
        log.info("关闭上游客户端: {}", taskId);
        
        return Mono.fromCallable(() -> {
            clientCache.remove(taskId);
            return client;
        })
        .flatMap(c -> c.close().thenReturn(true))
        .onErrorResume(e -> {
            log.error("关闭客户端失败: {} (原因: {})", taskId, e.getMessage());
            return Mono.just(false);
        });
    }
    
    /**
     * 关闭所有客户端，通常在系统关闭时调用
     */
    public Mono<Void> closeAllClients() {
        // 设置关闭标志
        isShuttingDown.set(true);
        
        log.info("正在关闭所有上游客户端，总数: {}", clientCache.size());
        
        return Flux.fromIterable(clientCache.keySet())
                .flatMap(this::closeClient)
                .then()
                .doFinally(signal -> log.info("所有上游客户端已关闭"));
    }
}