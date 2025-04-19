package io.secretarymcp.proxy.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.secretarymcp.proxy.client.UpstreamClient;
import io.secretarymcp.proxy.client.UpstreamClientConfig;
import io.secretarymcp.proxy.client.UpstreamClientFactory;
import io.secretarymcp.registry.UserSecretaryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import io.secretarymcp.storage.FileSystemStorage;

/**
 * SSE代理服务器
 * 负责通过SSE协议为MCP客户端提供服务
 */
@Component
@Profile("sse") // 只在SSE模式下激活
@Configuration
public class SseProxyServer {
    private static final Logger log = LoggerFactory.getLogger(SseProxyServer.class);
    
    // 对象映射器，用于JSON序列化/反序列化
    private final ObjectMapper objectMapper;

    // 上游客户端工厂，用于创建与上游服务的连接
    private final UpstreamClientFactory clientFactory;
    
    // 从配置中获取SSE端点路径，默认为/sse
    @Value("${mcp.sse.endpoint:/sse}")
    private String sseEndpoint;
    
    // 存储活动的上游客户端，使用ConcurrentHashMap确保线程安全
    private final ConcurrentHashMap<String, UpstreamClient> upstreamClients = new ConcurrentHashMap<>();
    
    // 初始化标志，使用AtomicBoolean确保线程安全
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    // MCP异步服务器实例
    private McpAsyncServer mcpServer;
    
    // 代理工具管理器，处理工具注册和卸载
    private ProxyToolManager toolManager;
    
    // 路由函数，用于处理HTTP请求
    private RouterFunction<?> routerFunction;
    
    // Spring环境配置，用于读取配置属性
    private final Environment environment;
    
    // 注入的WebFluxSseServerTransportProvider
    private final WebFluxSseServerTransportProvider transportProvider;
    
    // 添加FileSystemStorage字段
    private final FileSystemStorage storage;
    
    /**
     * 创建SSE代理服务器
     * 
     * @param objectMapper JSON处理器
     * @param environment 环境配置
     * @param transportProvider SSE传输提供者
     * @param storage 文件系统存储
     */
    public SseProxyServer(ObjectMapper objectMapper, Environment environment, 
                         WebFluxSseServerTransportProvider transportProvider,
                         FileSystemStorage storage) {
        this.objectMapper = objectMapper;
        this.clientFactory = new UpstreamClientFactory(objectMapper);
        this.environment = environment;
        this.transportProvider = transportProvider;
        this.storage = storage;
    }

    /**
     * 初始化代理服务器
     * 创建MCP服务器实例并注册系统工具
     * 
     * @return 初始化完成的信号
     */
    public Mono<Void> initialize() {
        if (initialized.compareAndSet(false, true)) {
            log.info("初始化SSE代理服务器");
            
            // 创建MCP服务器
            return createMcpServer()
                    .flatMap(server -> {
                        this.mcpServer = server;
                        this.toolManager = new ProxyToolManager(mcpServer);
                        
                        // 注册系统工具，传递Secretary名称
                        return toolManager.registerSystemTools(upstreamClients)
                                .doOnSuccess(v -> log.info("SSE代理服务器初始化完成"))
                                .doOnError(e -> {
                                    log.error("SSE代理服务器初始化失败: {}", e.getMessage(), e);
                                    initialized.set(false);
                                });
                    });
        } else {
            return Mono.empty(); // 已经初始化，直接返回
        }
    }

    /**
     * 创建MCP服务器
     * 
     * @return 创建好的MCP异步服务器
     */
    private Mono<McpAsyncServer> createMcpServer() {
        try {
            // 使用注入的storage创建UserSecretaryRegistry
            UserSecretaryRegistry registry = new UserSecretaryRegistry(this.storage);
            
            // 记录服务器信息
            log.info("创建SSE MCP服务器，使用已注入的transportProvider");
            
            // 创建MCP服务器 - 使用注入的transportProvider，传递registry
            McpAsyncServer asyncServer = McpServer.async(transportProvider)
                .serverInfo("mcp-secretary", "0.1.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                    .resources(false, true)
                    .tools(true)
                    .prompts(true)
                    .logging()
                    .build())
                .userSecretaryRegistry(registry)
                .build();
            
            return Mono.just(asyncServer);
        } catch (Exception e) {
            log.error("创建MCP服务器失败: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }
    
    /**
     * 获取SSE路由函数
     * 如果服务器尚未初始化，则先初始化
     */
    public RouterFunction<?> sseRouterFunction() {
        // 确保初始化完成
        if (!initialized.get()) {
            log.info("RouterFunction初始化中，确保SSE服务器已创建");
            try {
                initialize().block(Duration.ofSeconds(10));
            } catch (Exception e) {
                log.error("初始化过程中出错: {}", e.getMessage(), e);
            }
        }
        
        if (routerFunction == null) {
            throw new IllegalStateException("路由函数未创建");
        }
        
        log.info("返回SSE路由函数: {}", routerFunction.getClass().getName());
        return routerFunction;
    }
    
    /**
     * 检查服务器是否健康
     * 
     * @return 健康状态
     */
    public Mono<Boolean> isHealthy() {
        return Mono.just(initialized.get() && mcpServer != null);
    }

    /**
     * 同步上游工具
     * @param client 上游客户端
     * @param taskId 任务ID
     * @param taskName 任务名称
     * @param secretaryName Secretary名称
     */
    private Mono<Void> syncUpstreamTools(UpstreamClient client, String taskId, String taskName, String secretaryName) {
        log.info("同步上游工具: {} ({}) - Secretary: {}", taskName, taskId, secretaryName);
        
        return client.listTools()
                .doOnSubscribe(s -> log.info("开始获取上游工具列表: {} ({})", taskName, taskId))
                .doOnError(e -> log.error("获取上游工具列表失败 - 详细错误: {} ({})", taskName, taskId, e))
                .flatMap(tools -> {
                    log.info("获取到上游工具: {}, 工具数量: {}", taskName, tools.size());
                    
                    // 记录工具名称列表，帮助调试
                    if (!tools.isEmpty()) {
                        try {
                            List<String> toolNames = tools.stream()
                                    .map(McpSchema.Tool::name)
                                    .collect(java.util.stream.Collectors.toList());
                            log.info("上游工具列表: {} - {}", taskName, objectMapper.writeValueAsString(toolNames));
                        } catch (Exception e) {
                            log.warn("无法序列化工具名称: {}", e.getMessage());
                        }
                    }
                    
                    return toolManager.registerTaskProxyTools(secretaryName, taskId, taskName, tools, client)
                           .doOnSubscribe(s -> log.info("开始注册任务代理工具: {}, 工具数量: {}", taskName, tools.size()))
                           .doOnSuccess(v -> log.info("任务代理工具注册成功: {}, 工具数量: {}", taskName, tools.size()))
                           .doOnError(e -> log.error("注册任务代理工具失败 - 详细错误: {}", taskName, e));
                })
                .onErrorResume(e -> {
                    log.error("同步上游工具过程完全失败: {} ({})", taskName, taskId, e);
                    return Mono.error(e); // 重新抛出错误以便上层处理
                });
    }

    /**
     * 添加上游客户端
     */
    public Mono<Void> addUpstreamClient(UpstreamClientConfig config) {
        if (!initialized.get()) {
            return Mono.error(new IllegalStateException("SSE代理服务器未初始化"));
        }
        
        String taskId = config.getTaskId();
        String taskName = config.getTaskName();
        String secretaryName = config.getSecretaryName();
        
        // 如果没有指定任务名称，则使用ID
        if (taskName == null || taskName.isEmpty()) {
            taskName = taskId;
        }
        
        // 如果没有指定Secretary名称，则使用默认值
        if (secretaryName == null || secretaryName.isEmpty()) {
            secretaryName = "default"; // 或者其他适当的默认值
        }
        
        log.info("添加上游客户端: {} ({}) - Secretary: {}", taskName, taskId, secretaryName);
        
        // 记录配置详情，帮助诊断
        try {
            log.info("上游客户端配置: 类型={}, 连接配置={}", 
                    config.getConnectionProfile().getConnectionType(),
                    objectMapper.writeValueAsString(config.getConnectionProfile()));
        } catch (Exception e) {
            log.warn("无法序列化客户端配置: {}", e.getMessage());
        }
        
        // 如果已存在，先删除
        UpstreamClient existingClient = upstreamClients.get(taskId);
        Mono<Void> cleanupExisting = existingClient != null ?
                removeUpstreamClient(taskId) : Mono.empty();
        
        final String finalTaskName = taskName; // 用于lambda表达式
        final String finalSecretaryName = secretaryName; // 用于lambda表达式
        
        return cleanupExisting
                .then(Mono.defer(() -> 
                    clientFactory.getOrCreateClient(config)
                        .doOnSubscribe(s -> log.info("开始创建上游客户端: {} ({})", finalTaskName, taskId))
                        .doOnError(e -> {
                            // 详细记录创建客户端时的错误
                            log.error("创建上游客户端失败 - 详细错误: {} ({})", finalTaskName, taskId, e);
                            // 如果有嵌套异常，记录根本原因
                            Throwable rootCause = e;
                            while (rootCause.getCause() != null) {
                                rootCause = rootCause.getCause();
                            }
                            log.error("创建上游客户端失败 - 根本原因: {}", rootCause.getMessage());
                        })
                        .flatMap(client -> {
                            log.info("客户端已创建，正在存储和同步工具: {} ({})", finalTaskName, taskId);
                            // 存储客户端
                            upstreamClients.put(taskId, client);
                            log.info("客户端已缓存: {}", taskId);
                            
                            // 同步上游工具，传递Secretary名称
                            return syncUpstreamTools(client, taskId, finalTaskName, finalSecretaryName)
                                    .doOnSubscribe(s -> log.info("开始同步上游工具: {} ({})", finalTaskName, taskId))
                                    .doOnSuccess(v -> log.info("上游客户端添加成功: {} ({})", finalTaskName, taskId))
                                    .publishOn(Schedulers.boundedElastic())
                                    .doOnError(e -> {
                                        // 详细记录同步工具时的错误
                                        log.error("同步上游工具失败 - 详细错误: {} ({})", finalTaskName, taskId, e);
                                        // 记录系统资源状态
                                        log.info("系统资源状态 - 可用处理器: {}, 可用内存: {}MB", 
                                            Runtime.getRuntime().availableProcessors(),
                                            Runtime.getRuntime().freeMemory() / (1024 * 1024));
                                        
                                        log.error("添加上游客户端失败: {} ({}) (原因: {})", 
                                                finalTaskName, taskId, e.getMessage());
                                        upstreamClients.remove(taskId);
                                        client.close()
                                            .doOnError(ce -> log.warn("关闭失败的客户端时出错: {}", ce.getMessage()))
                                            .subscribe();
                                    });
                        })
                ))
                .then()
                .onErrorResume(e -> {
                    // 最终的错误处理，确保所有异常都被记录
                    log.error("添加上游客户端过程完全失败: {} ({})", finalTaskName, taskId, e);
                    return Mono.error(e); // 重新抛出错误以便上层处理
                });
    }

    /**
     * 移除上游客户端
     */
    public Mono<Void> removeUpstreamClient(String taskId) {
        if (!initialized.get()) {
            return Mono.error(new IllegalStateException("SSE代理服务器未初始化"));
        }
        
        UpstreamClient client = upstreamClients.get(taskId);
        if (client == null) {
            log.debug("上游客户端不存在: {}", taskId);
            return Mono.empty();
        }
        
        log.info("移除上游客户端: {}, 任务名称: {}", taskId, client.getTaskName());
        
        return Mono.fromCallable(() -> {
            upstreamClients.remove(taskId);
            return client;
        })
        .flatMap(c -> 
            // 注销工具
            toolManager.unregisterTaskProxyTools(taskId, client.getTaskName())
                .doOnSubscribe(s -> log.info("开始注销任务代理工具: {}/{}", client.getTaskName(), taskId))
                .doOnSuccess(v -> log.info("任务代理工具注销成功: {}/{}", client.getTaskName(), taskId))
                .doOnError(e -> log.error("注销任务代理工具失败: {}/{} - 详细错误:", 
                        client.getTaskName(), taskId, e))
                // 关闭客户端
                .then(c.close())
                .doOnSubscribe(s -> log.info("开始关闭客户端: {}", taskId))
                .doOnSuccess(v -> log.info("客户端关闭成功: {}", taskId))
                .doOnError(e -> log.error("关闭客户端失败: {} - 详细错误:", taskId, e))
                .doOnSuccess(v -> log.info("上游客户端移除成功: {}", taskId))
                .onErrorResume(e -> {
                    log.error("移除上游客户端失败: {} (原因: {})", taskId, e.getMessage(), e);
                    return Mono.empty();
                })
        );
    }

    /**
     * 获取所有上游客户端ID
     */
    public Mono<List<String>> getUpstreamClientIds() {
        return Mono.just(new ArrayList<>(upstreamClients.keySet()));
    }

    /**
     * 发送日志消息
     */
    public Mono<Void> sendLogMessage(McpSchema.LoggingLevel level, String message) {
        if (!initialized.get() || mcpServer == null) {
            log.error("无法发送日志消息，服务器未初始化");
            return Mono.empty();
        }
        
        McpSchema.LoggingMessageNotification notification = 
                McpSchema.LoggingMessageNotification.builder()
                    .level(level)
                    .logger("proxy-server")
                    .data(message)
                    .build();
        
        return mcpServer.loggingNotification(notification)
                .onErrorResume(e -> {
                    log.error("发送日志消息失败: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * 关闭代理服务器
     */
    public Mono<Void> shutdown() {
        if (!initialized.get()) {
            return Mono.empty();
        }
        
        log.info("关闭SSE代理服务器");
        
        initialized.set(false);
        
        // 分步关闭，先注销工具，再关闭客户端，最后关闭服务器
        return Mono.defer(() -> {
            if (toolManager != null) {
                return toolManager.unregisterAllTools();
            }
            return Mono.empty();
        })
        .then(Mono.defer(() -> {
            // 关闭所有上游客户端
            return Flux.fromIterable(upstreamClients.values())
                    .flatMap(UpstreamClient::close)
                    .doOnComplete(upstreamClients::clear)
                    .then();
        }))
        .then(Mono.defer(() -> {
            // 关闭MCP服务器
            if (mcpServer != null) {
                return mcpServer.closeGracefully();
            }
            return Mono.empty();
        }))
        .timeout(Duration.ofSeconds(5))
        .doOnSuccess(v -> log.info("SSE代理服务器已关闭"))
        .onErrorResume(e -> {
            log.error("SSE服务器操作失败: {}", e.getMessage(), e);
            return Mono.empty().then(Mono.error(new RuntimeException("SSE服务器错误", e)));
        });
    }

    /**
     * 运行代理服务器，等待事件循环
     */
    public Mono<Void> run() {
        if (!initialized.get()) {
            return Mono.error(new IllegalStateException("SSE代理服务器未初始化"));
        }
        
        log.info("SSE代理服务器运行中...");
        
        // 这个方法主要用于保持服务器运行，直到收到关闭信号
        return Mono.never();
    }

    /**
     * 获取MCP服务器实例
     * @return MCP异步服务器实例
     */
    public McpAsyncServer getMcpServer() {
        if (!initialized.get()) {
            throw new IllegalStateException("SSE代理服务器未初始化");
        }
        return this.mcpServer;
    }
}