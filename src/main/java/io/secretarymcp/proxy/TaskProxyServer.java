package io.secretarymcp.proxy;

import io.secretarymcp.model.RemoteTask;
import io.secretarymcp.proxy.client.UpstreamClientConfig;
import io.secretarymcp.proxy.server.StdioProxyServer;
import io.secretarymcp.util.Constants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务代理服务器
 * 负责连接任务服务与代理服务器，管理任务工具的注册和注销
 */
@Component
@RequiredArgsConstructor
public class TaskProxyServer {
    private static final Logger log = LoggerFactory.getLogger(TaskProxyServer.class);
    
    private final StdioProxyServer proxyServer;
    
    /**
     * 为任务注册工具
     * 
     * @param task 远程任务
     * @return 操作结果
     */
    public Mono<Void> registerTaskTools(RemoteTask task) {
        log.info("为任务注册工具: {}", task.getId());
        
        // 创建上游客户端配置
        UpstreamClientConfig config = buildClientConfig(task);
        
        // 添加上游客户端
        return proxyServer.addUpstreamClient(config)
                .doOnSuccess(v -> log.info("任务工具注册成功: {}", task.getId()))
                .doOnError(e -> log.error("任务工具注册失败: {} (原因: {})", task.getId(), e.getMessage()));
    }
    
    /**
     * 注销任务工具
     * 
     * @param taskId 任务ID
     * @return 操作结果
     */
    public Mono<Void> unregisterTaskTools(String taskId) {
        log.info("注销任务工具: {}", taskId);
        
        return proxyServer.removeUpstreamClient(taskId)
                .doOnSuccess(v -> log.info("任务工具注销成功: {}", taskId))
                .doOnError(e -> log.error("任务工具注销失败: {} (原因: {})", taskId, e.getMessage()));
    }
    
    /**
     * 根据任务构建上游客户端配置
     * 
     * @param task 远程任务
     * @return 上游客户端配置
     */
    private UpstreamClientConfig buildClientConfig(RemoteTask task) {
        // 获取连接参数，确保非空
        Map<String, Object> connectionParams = task.getConnectionParams();
        if (connectionParams == null) {
            connectionParams = new HashMap<>();
        }
        
        // 确定连接类型
        UpstreamClientConfig.ConnectionType connectionType = 
                task.getConnectionType() == Constants.TaskType.STDIO ? 
                UpstreamClientConfig.ConnectionType.STDIO : 
                UpstreamClientConfig.ConnectionType.SSE;
        
        // 从连接参数中获取超时配置
        Object timeoutObj = connectionParams.get("connectionTimeoutSeconds");
        int timeout = 60; // 默认值
        if (timeoutObj instanceof Number) {
            timeout = ((Number) timeoutObj).intValue();
        }
        
        // 创建配置构建器
        UpstreamClientConfig.Builder builder = UpstreamClientConfig.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .connectionType(connectionType)
                .connectionTimeoutSeconds(timeout);
        
        // 根据连接类型添加特定参数
        if (connectionType == UpstreamClientConfig.ConnectionType.STDIO) {
            // 命令是必需的
            String command = (String) connectionParams.get("command");
            builder.command(command);
            
            // 可选工作目录
            String workingDir = (String) connectionParams.get("workingDir");
            if (workingDir != null) {
                builder.workingDir(workingDir);
            }
            
            // 可选命令参数
            Object argsObj = connectionParams.get("commandArgs");
            if (argsObj instanceof List) {
                try {
                    @SuppressWarnings("unchecked")
                    List<String> commandArgs = (List<String>) argsObj;
                    builder.commandArgs(commandArgs);
                } catch (ClassCastException e) {
                    log.warn("任务 {} 的命令参数格式无效", task.getId());
                }
            }
            
            // 可选环境变量
            Object envObj = connectionParams.get("environmentVars");
            if (envObj instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> environmentVars = (Map<String, String>) envObj;
                    builder.environmentVars(environmentVars);
                } catch (ClassCastException e) {
                    log.warn("任务 {} 的环境变量格式无效", task.getId());
                }
            }
        } else {
            // 服务器URL是必需的
            String serverUrl = (String) connectionParams.get("serverUrl");
            if (serverUrl != null) {
                builder.serverUrl(serverUrl);
            }
            
            // 可选认证令牌
            String authToken = (String) connectionParams.get("authToken");
            if (authToken != null) {
                builder.authToken(authToken);
            }
            
            // 可选自定义头部
            Object headersObj = connectionParams.get("customHeaders");
            if (headersObj instanceof Map) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> customHeaders = (Map<String, String>) headersObj;
                    builder.customHeaders(customHeaders);
                } catch (ClassCastException e) {
                    log.warn("任务 {} 的自定义头部格式无效", task.getId());
                }
            }
        }
        
        // 高级配置
        Map<String, Object> config = task.getConfig();
        if (config != null) {
            // 启用根功能
            Object rootsObj = config.get("enableRoots");
            if (rootsObj instanceof Boolean) {
                builder.enableRoots((Boolean) rootsObj);
            } else {
                builder.enableRoots(false); // 默认值
            }
            
            // 启用采样功能
            Object samplingObj = config.get("enableSampling");
            if (samplingObj instanceof Boolean) {
                builder.enableSampling((Boolean) samplingObj);
            } else {
                builder.enableSampling(false); // 默认值
            }
        }
        
        return builder.build();
    }
}