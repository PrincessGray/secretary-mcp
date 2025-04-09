package io.secretarymcp.proxy.client;

import io.modelcontextprotocol.spec.McpSchema;
import io.secretarymcp.model.ConnectionProfile;
import io.secretarymcp.model.GeneralConfig;
import io.secretarymcp.model.RemoteTask;
import io.secretarymcp.model.SseConfig;
import io.secretarymcp.model.StdioConfig;
import io.secretarymcp.util.Constants;
import lombok.Data;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * 上游客户端配置 - 用于与上游服务通信的配置
 */
@Data
public class UpstreamClientConfig {
    // 任务标识
    private String taskId;            // 任务ID（内部使用）
    private String taskName;          // 任务名称（对用户友好）
    
    // 使用统一的ConnectionProfile替代分散的连接配置
    private ConnectionProfile connectionProfile;
    
    // 采样处理器（如果启用采样）
    private Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>> samplingHandler;
    
    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return taskName != null && !taskName.isEmpty() ? taskName : taskId;
    }
    
    /**
     * 从RemoteTask创建上游客户端配置
     * @param task 远程任务
     * @return 上游客户端配置
     * @throws IllegalArgumentException 如果任务为null或配置不完整
     */
    public static UpstreamClientConfig fromRemoteTask(RemoteTask task) {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为空");
        }
        
        if (task.getId() == null || task.getId().isEmpty()) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        
        if (task.getConnectionProfile() == null) {
            throw new IllegalArgumentException("任务连接配置不能为空");
        }
        
        UpstreamClientConfig config = new UpstreamClientConfig();
        config.setTaskId(task.getId());
        config.setTaskName(task.getName() != null ? task.getName() : task.getId());
        
        // 复制ConnectionProfile (深拷贝)
        ConnectionProfile originalProfile = task.getConnectionProfile();
        ConnectionProfile profile = new ConnectionProfile();
        
        // 复制基本属性
        profile.setConnectionType(originalProfile.getConnectionType());
        profile.setConnectionTimeoutSeconds(originalProfile.getConnectionTimeoutSeconds());
        
        // 复制通用配置
        if (originalProfile.getGeneralConfig() != null) {
            GeneralConfig generalConfig = new GeneralConfig();
            GeneralConfig original = originalProfile.getGeneralConfig();
            
            // 复制标准字段
            generalConfig.setTimeoutSeconds(original.getTimeoutSeconds());
            generalConfig.setRetryCount(original.getRetryCount());
            generalConfig.setRetryDelaySeconds(original.getRetryDelaySeconds());
            generalConfig.setEnableRoots(original.getEnableRoots());
            generalConfig.setEnableSampling(original.getEnableSampling());
            generalConfig.setLoggingLevel(original.getLoggingLevel());
            generalConfig.setVerboseMode(original.getVerboseMode());
            
            // 复制自定义设置
            if (original.getCustomSettings() != null) {
                generalConfig.setCustomSettings(new java.util.HashMap<>(original.getCustomSettings()));
            }
            
            profile.setGeneralConfig(generalConfig);
        } else {
            // 确保有默认通用配置
            profile.setGeneralConfig(GeneralConfig.createDefault());
        }
        
        // 根据连接类型复制特定配置
        if (originalProfile.getConnectionType() == Constants.ConnectionType.STDIO) {
            StdioConfig stdioConfig = new StdioConfig();
            if (originalProfile.getStdioConfig() != null) {
                StdioConfig original = originalProfile.getStdioConfig();
                
                stdioConfig.setCommand(original.getCommand());
                
                if (original.getCommandArgs() != null) {
                    stdioConfig.setCommandArgs(new java.util.ArrayList<>(original.getCommandArgs()));
                }
                
                if (original.getEnvironmentVars() != null) {
                    stdioConfig.setEnvironmentVars(new java.util.HashMap<>(original.getEnvironmentVars()));
                }
            }
            
            // 验证STDIO配置的完整性
            if (stdioConfig.getCommand() == null || stdioConfig.getCommand().isEmpty()) {
                throw new IllegalArgumentException("STDIO任务必须提供命令");
            }
            
            profile.setStdioConfig(stdioConfig);
        } else if (originalProfile.getConnectionType() == Constants.ConnectionType.SSE) {
            SseConfig sseConfig = new SseConfig();
            if (originalProfile.getSseConfig() != null) {
                SseConfig original = originalProfile.getSseConfig();
                
                sseConfig.setServerUrl(original.getServerUrl());
                sseConfig.setAuthToken(original.getAuthToken());
                
                if (original.getCustomHeaders() != null) {
                    sseConfig.setCustomHeaders(new java.util.HashMap<>(original.getCustomHeaders()));
                }
            }
            
            // 验证SSE配置的完整性
            if (sseConfig.getServerUrl() == null || sseConfig.getServerUrl().isEmpty()) {
                throw new IllegalArgumentException("SSE任务必须提供服务器URL");
            }
            
            profile.setSseConfig(sseConfig);
        } else {
            throw new IllegalArgumentException("不支持的连接类型: " + originalProfile.getConnectionType());
        }
        
        config.setConnectionProfile(profile);

//TODO        // 设置采样处理器（如果GeneralConfig中启用了采样）
//        if (profile.getGeneralConfig() != null &&
//            Boolean.TRUE.equals(profile.getGeneralConfig().getEnableSampling())) {
//            // 设置默认的采样处理器，或者根据需要自定义
//            config.setSamplingHandler(createDefaultSamplingHandler());
//        }

        return config;
    }

    /**
     * 验证配置是否完整有效
     * @throws IllegalArgumentException 如果配置不完整或无效
     */
    public void validate() {
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("任务ID不能为空");
        }
        
        if (connectionProfile == null) {
            throw new IllegalArgumentException("连接配置不能为空");
        }
        
        // 确保有通用配置
        if (connectionProfile.getGeneralConfig() == null) {
            connectionProfile.setGeneralConfig(GeneralConfig.createDefault());
        }
        
        // 根据连接类型验证特定配置
        if (connectionProfile.getConnectionType() == Constants.ConnectionType.STDIO) {
            if (connectionProfile.getStdioConfig() == null) {
                throw new IllegalArgumentException("STDIO连接类型必须提供StdioConfig");
            }
            
            StdioConfig stdioConfig = connectionProfile.getStdioConfig();
            if (stdioConfig.getCommand() == null || stdioConfig.getCommand().isEmpty()) {
                throw new IllegalArgumentException("STDIO配置必须提供命令");
            }
        } else if (connectionProfile.getConnectionType() == Constants.ConnectionType.SSE) {
            if (connectionProfile.getSseConfig() == null) {
                throw new IllegalArgumentException("SSE连接类型必须提供SseConfig");
            }
            
            SseConfig sseConfig = connectionProfile.getSseConfig();
            if (sseConfig.getServerUrl() == null || sseConfig.getServerUrl().isEmpty()) {
                throw new IllegalArgumentException("SSE配置必须提供服务器URL");
            }
        } else {
            throw new IllegalArgumentException("不支持的连接类型: " + connectionProfile.getConnectionType());
        }
        
        // 验证采样处理器
        if (connectionProfile.getGeneralConfig().getEnableSampling() != null &&
            connectionProfile.getGeneralConfig().getEnableSampling() &&
            samplingHandler == null) {
            throw new IllegalArgumentException("启用采样时必须提供采样处理器");
        }
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
        
        // 初始化ConnectionProfile
        {
            instance.connectionProfile = new ConnectionProfile();
            instance.connectionProfile.setGeneralConfig(GeneralConfig.createDefault());
        }
        
        // 任务标识
        public Builder taskId(String taskId) {
            instance.taskId = taskId;
            return this;
        }
        
        public Builder taskName(String taskName) {
            instance.taskName = taskName;
            return this;
        }
        
        // ConnectionProfile设置
        public Builder connectionProfile(ConnectionProfile profile) {
            instance.connectionProfile = profile;
            return this;
        }
        
        public Builder connectionType(Constants.ConnectionType type) {
            instance.connectionProfile.setConnectionType(type);
            return this;
        }
        
        public Builder connectionTimeoutSeconds(int seconds) {
            instance.connectionProfile.setConnectionTimeoutSeconds(seconds);
            return this;
        }
        
        // StdioConfig便捷方法
        public Builder stdioCommand(String command) {
            ensureStdioConfig().setCommand(command);
            return this;
        }
        
        public Builder stdioCommandArgs(java.util.List<String> args) {
            ensureStdioConfig().setCommandArgs(args);
            return this;
        }
        
        public Builder stdioEnvironmentVars(java.util.Map<String, String> vars) {
            ensureStdioConfig().setEnvironmentVars(vars);
            return this;
        }
        
        // SseConfig便捷方法
        public Builder sseServerUrl(String url) {
            ensureSseConfig().setServerUrl(url);
            return this;
        }
        
        public Builder sseAuthToken(String token) {
            ensureSseConfig().setAuthToken(token);
            return this;
        }
        
        public Builder sseCustomHeaders(java.util.Map<String, String> headers) {
            ensureSseConfig().setCustomHeaders(headers);
            return this;
        }
        
        // GeneralConfig便捷方法
        public Builder enableRoots(boolean enable) {
            ensureGeneralConfig().setEnableRoots(enable);
            return this;
        }
        
        public Builder enableSampling(boolean enable) {
            ensureGeneralConfig().setEnableSampling(enable);
            return this;
        }
        
        public Builder timeoutSeconds(int seconds) {
            ensureGeneralConfig().setTimeoutSeconds(seconds);
            return this;
        }
        
        public Builder samplingHandler(Function<McpSchema.CreateMessageRequest, Mono<McpSchema.CreateMessageResult>> handler) {
            instance.samplingHandler = handler;
            return this;
        }
        
        // 辅助方法
        private StdioConfig ensureStdioConfig() {
            instance.connectionProfile.setConnectionType(Constants.ConnectionType.STDIO);
            if (instance.connectionProfile.getStdioConfig() == null) {
                instance.connectionProfile.setStdioConfig(new StdioConfig());
            }
            return instance.connectionProfile.getStdioConfig();
        }
        
        private SseConfig ensureSseConfig() {
            instance.connectionProfile.setConnectionType(Constants.ConnectionType.SSE);
            if (instance.connectionProfile.getSseConfig() == null) {
                instance.connectionProfile.setSseConfig(new SseConfig());
            }
            return instance.connectionProfile.getSseConfig();
        }
        
        private GeneralConfig ensureGeneralConfig() {
            if (instance.connectionProfile.getGeneralConfig() == null) {
                instance.connectionProfile.setGeneralConfig(GeneralConfig.createDefault());
            }
            return instance.connectionProfile.getGeneralConfig();
        }
        
        // 构建方法
        public UpstreamClientConfig build() {
            // 验证配置的完整性
            instance.validate();
            return instance;
        }
    }
}