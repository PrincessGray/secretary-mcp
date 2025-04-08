package io.secretarymcp.proxy.client;

import io.modelcontextprotocol.spec.McpSchema;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 上游客户端配置
 */
@Data
public class UpstreamClientConfig {
    // 通用配置
    private String taskId;            // 任务ID（内部使用）
    private String taskName;          // 任务名称（对用户友好）
    private ConnectionType connectionType;
    private int connectionTimeoutSeconds = 60;
    
    // STDIO连接配置
    private String command;
    private List<String> commandArgs;
    private Map<String, String> environmentVars;
    private String workingDir;
    
    // SSE连接配置
    private String serverUrl;
    private String authToken;
    private Map<String, String> customHeaders;
    
    // 高级MCP能力配置
    private boolean enableRoots;
    private boolean enableSampling;
    
    // 采样处理器（如果启用采样）
    private Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>> samplingHandler;
    
    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return taskName != null && !taskName.isEmpty() ? taskName : taskId;
    }
    
    /**
     * 连接类型
     */
    public enum ConnectionType {
        STDIO,
        SSE
    }
    
    // 显式创建静态builder方法
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder类的显式实现
     */
    public static class Builder {
        private final UpstreamClientConfig instance = new UpstreamClientConfig();
        
        // 通用配置
        public Builder taskId(String taskId) {
            instance.taskId = taskId;
            return this;
        }
        
        public Builder taskName(String taskName) {
            instance.taskName = taskName;
            return this;
        }
        
        public Builder connectionType(ConnectionType connectionType) {
            instance.connectionType = connectionType;
            return this;
        }
        
        public Builder connectionTimeoutSeconds(int connectionTimeoutSeconds) {
            instance.connectionTimeoutSeconds = connectionTimeoutSeconds;
            return this;
        }
        
        // STDIO连接配置
        public Builder command(String command) {
            instance.command = command;
            return this;
        }
        
        public Builder commandArgs(List<String> commandArgs) {
            instance.commandArgs = commandArgs;
            return this;
        }
        
        public Builder environmentVars(Map<String, String> environmentVars) {
            instance.environmentVars = environmentVars;
            return this;
        }
        
        public Builder workingDir(String workingDir) {
            instance.workingDir = workingDir;
            return this;
        }
        
        // SSE连接配置
        public Builder serverUrl(String serverUrl) {
            instance.serverUrl = serverUrl;
            return this;
        }
        
        public Builder authToken(String authToken) {
            instance.authToken = authToken;
            return this;
        }
        
        public Builder customHeaders(Map<String, String> customHeaders) {
            instance.customHeaders = customHeaders;
            return this;
        }
        
        // 高级MCP能力配置
        public Builder enableRoots(boolean enableRoots) {
            instance.enableRoots = enableRoots;
            return this;
        }
        
        public Builder enableSampling(boolean enableSampling) {
            instance.enableSampling = enableSampling;
            return this;
        }
        
        public Builder samplingHandler(Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>> samplingHandler) {
            instance.samplingHandler = samplingHandler;
            return this;
        }
        
        // 构建方法
        public UpstreamClientConfig build() {
            return instance;
        }
    }
}