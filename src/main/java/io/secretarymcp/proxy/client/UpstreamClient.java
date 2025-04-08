package io.secretarymcp.proxy.client;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import reactor.core.publisher.Mono;
import java.util.Map;
import java.util.List;
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
    private boolean initialized;
    
    private static final Logger log = LoggerFactory.getLogger(UpstreamClient.class);
    
    public UpstreamClient(String taskId, String taskName,   McpAsyncClient client, boolean initialized) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.client = client;
        this.initialized = initialized;
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
        return client.closeGracefully()
                .doOnSuccess(v -> initialized = false);
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
}