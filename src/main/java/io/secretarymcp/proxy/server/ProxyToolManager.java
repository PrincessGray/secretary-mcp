package io.secretarymcp.proxy.server;

import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.secretarymcp.proxy.client.UpstreamClient;
import io.secretarymcp.util.Constants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 代理工具管理器
 */
@RequiredArgsConstructor
public class ProxyToolManager {
    private static final Logger log = LoggerFactory.getLogger(ProxyToolManager.class);
    
    private final McpAsyncServer mcpServer;
    
    // 已注册的工具名称
    private final CopyOnWriteArrayList<String> registeredTools = new CopyOnWriteArrayList<>();
    
    /**
     * 注册系统工具
     */
    public Mono<Void> registerSystemTools(Map<String, UpstreamClient> upstreamClients) {
        log.info("注册系统工具");
        
        // 创建系统状态工具规范
        McpServerFeatures.AsyncToolSpecification statusToolSpec = 
                ProxyTools.createSystemStatusToolSpec(upstreamClients);
        
        // 记录工具名称
        registeredTools.add(ProxyTools.createSystemStatusTool().getName());
        
        // 注册工具
        return mcpServer.addTool(statusToolSpec)
                .doOnSuccess(v -> log.info("系统工具注册成功: {}", 
                        ProxyTools.createSystemStatusTool().getName()))
                .doOnError(e -> log.error("注册系统工具失败: {}", e.getMessage()));
    }
    
    /**
     * 为任务注册代理工具
     */
    public Mono<Void> registerTaskProxyTools(String taskId, String taskName, List<Tool> tools, UpstreamClient client) {
        log.info("注册任务代理工具: {}, 工具数量: {}", taskName, tools.size());
        
        List<McpServerFeatures.AsyncToolSpecification> toolSpecs = new ArrayList<>();
        List<String> toolNames = new ArrayList<>();
        
        // 为每个上游工具创建代理工具
        for (Tool tool : tools) {
            String proxyToolName = Constants.Mcp.TOOL_PREFIX + taskName + "." + tool.name();
            
            // 创建工具规范
            McpServerFeatures.AsyncToolSpecification toolSpec = 
                    ProxyTools.createProxyToolSpec(taskId, taskName, tool, client);
            
            toolSpecs.add(toolSpec);
            toolNames.add(proxyToolName);
        }
        
        // 记录工具名称
        registeredTools.addAll(toolNames);
        
        // 批量注册工具
        return Flux.fromIterable(toolSpecs)
                .flatMap(mcpServer::addTool)
                .then()
                .doOnSuccess(v -> log.info("任务代理工具注册成功: {}, 工具数量: {}", taskName, tools.size()))
                .doOnError(e -> log.error("注册任务代理工具失败: {}", e.getMessage()));
    }
    
    /**
     * 注销特定任务的所有代理工具
     */
    public Mono<Void> unregisterTaskProxyTools(String taskId, String taskName) {
        log.info("注销任务代理工具: {}", taskName);
        
        String toolPrefix = Constants.Mcp.TOOL_PREFIX + taskName + ".";
        
        // 找出所有属于该任务的工具
        List<String> toolsToRemove = new ArrayList<>();
        for (String toolName : registeredTools) {
            if (toolName.startsWith(toolPrefix)) {
                toolsToRemove.add(toolName);
            }
        }
        
        if (toolsToRemove.isEmpty()) {
            log.info("没有找到任务的代理工具: {}", taskName);
            return Mono.empty();
        }
        
        // 注销工具
        return Flux.fromIterable(toolsToRemove)
                .flatMap(toolName -> mcpServer.removeTool(toolName)
                        .doOnSuccess(v -> {
                            registeredTools.remove(toolName);
                            log.debug("已注销代理工具: {}", toolName);
                        })
                        .onErrorResume(e -> {
                            log.error("注销代理工具失败: {} (原因: {})", toolName, e.getMessage());
                            return Mono.empty();
                        })
                )
                .then()
                .doOnSuccess(v -> log.info("任务代理工具注销完成: {}, 工具数量: {}", 
                        taskName, toolsToRemove.size()));
    }
    
    /**
     * 注销所有工具
     */
    public Mono<Void> unregisterAllTools() {
        log.info("注销所有代理工具");
        
        List<String> toolsToRemove = new ArrayList<>(registeredTools);
        
        return Flux.fromIterable(toolsToRemove)
                .flatMap(toolName -> mcpServer.removeTool(toolName)
                        .doOnSuccess(v -> {
                            registeredTools.remove(toolName);
                            log.debug("已注销代理工具: {}", toolName);
                        })
                        .onErrorResume(e -> {
                            log.error("注销代理工具失败: {} (原因: {})", toolName, e.getMessage());
                            return Mono.empty();
                        })
                )
                .then()
                .doOnSuccess(v -> log.info("所有代理工具注销完成, 工具数量: {}", toolsToRemove.size()));
    }
}