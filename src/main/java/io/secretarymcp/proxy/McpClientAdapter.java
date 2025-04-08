package io.secretarymcp.proxy;

import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * MCP客户端适配器接口
 * 抽象与远程MCP服务器的交互，提供任务管理和工具调用功能
 */
public interface McpClientAdapter {
    
    /**
     * 激活远程任务
     * 
     * @param taskId 任务ID
     * @param config 任务配置
     * @return 激活是否成功
     */
    Mono<Boolean> activateTask(String taskId, Map<String, Object> config);
    
    /**
     * 停用远程任务
     * 
     * @param taskId 任务ID
     * @return 停用是否成功
     */
    Mono<Boolean> deactivateTask(String taskId);
    
    /**
     * 调用任务工具
     * 
     * @param taskId 任务ID
     * @param toolName 工具名称
     * @param args 工具参数
     * @return 工具调用结果
     */
    Mono<Map<String, Object>> callTaskTool(String taskId, String toolName, Map<String, Object> args);
    
    /**
     * 获取任务状态
     * 
     * @param taskId 任务ID
     * @return 任务状态信息
     */
    Mono<Map<String, Object>> getTaskStatus(String taskId);
}