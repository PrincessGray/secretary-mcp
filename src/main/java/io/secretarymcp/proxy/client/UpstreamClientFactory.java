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
                            .thenReturn(new UpstreamClient(config.getTaskId(), config.getTaskName(), client, true));
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
        
        return Mono.fromCallable(() -> {
            log.info("创建STDIO传输层: {} -> {}", config.getTaskId(), stdioConfig.getCommand());
            
            // 创建服务器参数构建器
            ServerParameters.Builder paramsBuilder = ServerParameters.builder(stdioConfig.getCommand());
            
            // 添加命令行参数
            if (stdioConfig.getCommandArgs() != null && !stdioConfig.getCommandArgs().isEmpty()) {
                paramsBuilder.args(stdioConfig.getCommandArgs());
            }
            
            // 添加环境变量
            if (stdioConfig.getEnvironmentVars() != null && !stdioConfig.getEnvironmentVars().isEmpty()) {
                paramsBuilder.env(stdioConfig.getEnvironmentVars());
            }
            
            // 通过环境变量传递工作目录
            String workingDir = stdioConfig.getWorkingDir();
            if (workingDir != null && !workingDir.isEmpty()) {
                paramsBuilder.addEnvVar("WORKING_DIR", workingDir);
            }
            
            // 构建服务器参数并创建STDIO传输层
            ServerParameters params = paramsBuilder.build();
            return (McpClientTransport) new StdioClientTransport(params);
        })
        .doOnError(e -> log.error("创建STDIO传输层失败: {}", e.getMessage()));
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
            log.info("创建SSE传输层: {} -> {}", config.getTaskId(), sseConfig.getServerUrl());
            
            // 创建WebClient构建器
            WebClient.Builder webClientBuilder = WebClient.builder()
                    .baseUrl(sseConfig.getServerUrl());
            
            // 添加自定义头部
            if (sseConfig.getCustomHeaders() != null) {
                sseConfig.getCustomHeaders().forEach((key, value) -> {
                    if (key != null && value != null) {
                        webClientBuilder.defaultHeader(key, value);
                    }
                });
            }
            
            // 添加认证Token
            if (sseConfig.getAuthToken() != null && !sseConfig.getAuthToken().isEmpty()) {
                webClientBuilder.defaultHeader("Authorization", "Bearer " + sseConfig.getAuthToken());
            }
            
            // 直接创建并返回SSE传输层，更简洁
            return (McpClientTransport) new WebFluxSseClientTransport(webClientBuilder);
        })
        .doOnError(e -> log.error("创建SSE传输层失败: {}", e.getMessage()));
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

        // 配置资源变更消费者 - 简化为表达式lambda
        builder.resourcesChangeConsumer(resources ->
                Mono.fromRunnable(() -> log.info("资源已更新，任务ID: {}，资源数量: {}",
                        config.getTaskId(), resources.size()))
        );

        // 配置提示变更消费者 - 简化为表达式lambda
        builder.promptsChangeConsumer(prompts ->
                Mono.fromRunnable(() -> log.info("提示已更新，任务ID: {}，提示数量: {}",
                        config.getTaskId(), prompts.size()))
        );
        
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