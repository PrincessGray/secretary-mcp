package io.secretarymcp.proxy.server;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.secretarymcp.proxy.client.UpstreamClient;
import io.secretarymcp.util.Constants;
import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

/**
 * 代理工具定义和处理器
 */
public class ProxyTools {
    private static final Logger log = LoggerFactory.getLogger(ProxyTools.class);

    /**
     * 代理工具配置
     */
    @Data
    @Builder
    public static class ProxyToolConfig {
        private String name;              // 工具名称
        private String description;       // 工具描述
        private McpSchema.JsonSchema inputSchema;  // 输入模式
    }
    
    /**
     * 创建系统状态工具配置
     */
    public static ProxyToolConfig createSystemStatusTool() {
        return ProxyToolConfig.builder()
                .name(STR."system_status")
                .description("获取代理系统状态信息")
                .inputSchema(createEmptySchema())
                .build();
    }
    
    // 创建一个空的输入模式
    private static McpSchema.JsonSchema createEmptySchema() {
        return new McpSchema.JsonSchema(
            "object",                    // type
            Map.of(),                    // 空的properties
            List.of(),                   // 空的required
            false                        // 不允许additionalProperties
        );
    }
    
    /**
     * 创建系统状态工具规范
     * @param upstreamClients 上游客户端映射
     */
    public static McpServerFeatures.AsyncToolSpecification createSystemStatusToolSpec(Map<String, UpstreamClient> upstreamClients) {
        Tool tool = new Tool(
                createSystemStatusTool().getName(),
                createSystemStatusTool().getDescription(),
                createSystemStatusTool().getInputSchema()
        );
        
        return new McpServerFeatures.AsyncToolSpecification(
                tool,
                (exchange, args) -> {
                    Map<String, Object> statusInfo = Map.of(
                            "time", java.time.LocalDateTime.now().toString(),
                            "upstreamCount", upstreamClients.size(),
                            "upstreamClients", upstreamClients.keySet()
                    );
                    
                    // 将状态信息转换为JSON字符串
                    String statusJson = io.secretarymcp.util.JsonUtils.toJson(statusInfo);

                    // 创建文本内容
                    List<McpSchema.Content> content = List.of(
                        new McpSchema.TextContent(statusJson)
                    );
                    
                    // 使用内容列表创建调用结果
                    return Mono.just(new McpSchema.CallToolResult(content, false));
                }
        );
    }


    /**
     * 为上游工具创建代理工具规范
     * @param secretaryName Secretary名称
     * @param taskId 任务ID
     * @param taskName 任务名称
     * @param upstreamTool 上游工具
     * @param upstreamClient 上游客户端
     */
    public static McpServerFeatures.AsyncToolSpecification createProxyToolSpec(
            String secretaryName, String taskId, String taskName, Tool upstreamTool, UpstreamClient upstreamClient) {
        
        // 创建代理工具，使用下划线分隔的命名格式
        String proxyName = STR."\{secretaryName}_\{taskName}_\{upstreamTool.name()}";
        String proxyDesc = STR."[\{taskName}] \{upstreamTool.description()}";
        
        Tool proxyTool = new Tool(
                proxyName,
                proxyDesc,
                upstreamTool.inputSchema()
        );
        
        // 创建工具规范，直接转发到上游客户端
        return new McpServerFeatures.AsyncToolSpecification(
                proxyTool,
                (McpAsyncServerExchange exchange, Map<String, Object> args) -> {
                    log.debug("转发工具调用到上游服务器: {}.{}", taskName, upstreamTool.name());
                    return upstreamClient.callTool(new McpSchema.CallToolRequest(upstreamTool.name(), args))
                            .onErrorResume(e -> {
                                log.error("调用上游工具失败: {}.{} (原因: {})",
                                        taskName, upstreamTool.name(), e.getMessage());
                                
                                // 创建错误内容
                                List<McpSchema.Content> errorContent = List.of(
                                    new McpSchema.TextContent(STR."上游服务器调用失败: \{e.getMessage()}")
                                );
                                
                                // 返回带有错误标记的结果
                                return Mono.just(new McpSchema.CallToolResult(errorContent, true));
                            });
                }
        );
    }
}