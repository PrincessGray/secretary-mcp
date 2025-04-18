package io.secretarymcp.proxy.client;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.secretarymcp.util.Constants;
import lombok.Getter;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.Disposable;
import java.util.List;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 上游客户端包装类
 */
@Getter
public class UpstreamClient {
    private final String taskId;
    private final String taskName;
    private final McpAsyncClient client;
    private final Constants.ConnectionType connectionType;
    private boolean initialized;
    
    private static final Logger log = LoggerFactory.getLogger(UpstreamClient.class);
    
    // 心跳相关字段
    private Disposable pingScheduler;
    private final Duration pingInterval = Duration.ofSeconds(30); // 30秒发送一次心跳
    
    public UpstreamClient(String taskId, String taskName, McpAsyncClient client, boolean initialized, Constants.ConnectionType connectionType) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.client = client;
        this.initialized = initialized;
        this.connectionType = connectionType;
        
        // 如果是SSE连接且已初始化，则启动心跳
        if (initialized && isSSEConnection()) {
            startPingScheduler();
        }
    }
    
    // 判断是否为SSE连接 - 通过连接类型判断
    private boolean isSSEConnection() {
        // 从连接配置中获取连接类型
        return connectionType == Constants.ConnectionType.SSE;
    }
    
    /**
     * 启动ping调度器
     */
    public void startPingScheduler() {
        // 如果已有调度器，先停止
        stopPingScheduler();
        
        // 只为SSE连接启动心跳
        if (isSSEConnection()) {
            log.info("启动MCP心跳服务: {} ({}), 间隔: {}秒", taskName, taskId, pingInterval.getSeconds());
            
            pingScheduler = Flux.interval(pingInterval)
                .flatMap(tick -> {
                    log.debug("发送MCP心跳: {} ({})", taskName, taskId);
                    return ping()
                        .timeout(Duration.ofSeconds(10)) // 设置超时
                        .doOnSuccess(isSuccessful -> {
                            if (isSuccessful) {
                                log.debug("心跳成功: {} ({})", taskName, taskId);
                            } else {
                                log.warn("心跳返回失败: {} ({})", taskName, taskId);
                            }
                        })
                        .onErrorResume(e -> {
                            log.warn("心跳失败: {} ({}) - 原因: {}", taskName, taskId, e.getMessage());
                            return Mono.just(false);
                        });
                })
                .subscribe(
                    null,
                    error -> log.error("心跳服务出错: {} ({})", taskName, taskId, error),
                    () -> log.info("心跳服务正常停止: {} ({})", taskName, taskId)
                );
        }
    }
    
    /**
     * 停止ping调度器
     */
    private void stopPingScheduler() {
        if (pingScheduler != null && !pingScheduler.isDisposed()) {
            pingScheduler.dispose();
            pingScheduler = null;
            log.info("已停止心跳服务: {} ({})", taskName, taskId);
        }
    }
    
    /**
     * 调用工具 - 基本方法
     */
    public Mono<McpSchema.CallToolResult> callTool(McpSchema.CallToolRequest request) {
        return client.callTool(request);
    }
    
    /**
     * 关闭客户端
     */
    public Mono<Void> close() {
        // 先停止心跳
        stopPingScheduler();
        initialized = false;
        
        return client.closeGracefully()
                .doOnSuccess(v -> log.info("客户端已成功关闭: {} ({})", taskName, taskId))
                .doOnError(e -> log.error("关闭客户端时出错: {} ({})", taskName, taskId, e));
    }
    
    /**
     * 健康检查
     */
    public Mono<Boolean> ping() {
        return client.ping()
                .map(r -> true)
                .onErrorReturn(false);
    }
    
    /**
     * 列出可用工具
     */
    public Mono<List<McpSchema.Tool>> listTools() {
        return client.listTools()
            .map(McpSchema.ListToolsResult::tools) // 从ListToolsResult中提取工具列表
            .doOnNext(tools -> {
                if (log.isDebugEnabled()) {
                    log.debug("上游服务器 {} 提供的工具: {}", taskId, tools.size());
                    tools.forEach(tool -> 
                        log.debug("  - 工具: {} ({})", tool.name(), tool.description()));
                }
            });
    }

    public Mono<McpSchema.ListPromptsResult> listPrompts() {
        return this.client.listPrompts();
    }

    public Mono<McpSchema.GetPromptResult> getPrompt(McpSchema.GetPromptRequest request) {
        return this.client.getPrompt(request);
    }
    
    /**
     * 连接状态检查
     */
    public boolean isActive() {
        return initialized;
    }
}