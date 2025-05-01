package io.secretarymcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.secretarymcp.util.Constants;
import io.secretarymcp.util.Constants.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 远程任务，基于任务模板创建
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteTask {
    // 基本信息
    private String id;
    private String name;
    private String description;
    private String secretaryId;
    private String secretaryName;
    private String templateId;
    private TaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 使用ConnectionProfile替代直接的连接参数
    private ConnectionProfile connectionProfile;
    
    // 保存模板中定义的可定制参数
    private List<TaskTemplate.ConfigParam> customizableParams;
    
    // 任务配置
    private Map<String, Object> config;
    
    // 元数据
    private Map<String, Object> metadata;
    
    /**
     * 从模板创建任务
     */
    public static RemoteTask fromTemplate(TaskTemplate template, String secretaryId, String secretaryName, String name) {
        String taskName = name != null && !name.isBlank() ? name : template.getName();
        
        RemoteTask task = RemoteTask.builder()
                .id(UUID.randomUUID().toString())
                .name(taskName)
                .description(template.getDescription())
                .secretaryId(secretaryId)
                .secretaryName(secretaryName)
                .templateId(template.getId())
                .status(TaskStatus.INACTIVE)
                .customizableParams(new ArrayList<>(template.getCustomizableParams()))
                .config(new HashMap<>(template.getDefaultConfig()))
                .metadata(new HashMap<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // 深拷贝ConnectionProfile
        task.setConnectionProfile(deepCopy(template.getConnectionProfile()));
        
        // 处理所有可定制参数
        if (task.customizableParams != null) {
            for (TaskTemplate.ConfigParam param : task.customizableParams) {
                if (param.getDefaultValue() == null) {
                    continue;
                }
                
                String value = String.valueOf(param.getDefaultValue());
                
                // 根据参数类别和名称进行处理
                switch (param.getCategory()) {
                    case STDIO_ENV:
                        task.applyStdioEnvParam(param.getName(), value);
                        break;
                    case STDIO_ARG:
                        task.applyStdioArgParam(param, Boolean.parseBoolean(value));
                        break;
                    case SSE_CONNECTION:
                        // 处理SSE连接参数
                        if ("serverUrl".equals(param.getName())) {
                            task.getConnectionProfile().getSseConfig().setServerUrl(value);
                        } else if ("bearerToken".equals(param.getName())) {
                            task.getConnectionProfile().getSseConfig().setBearerToken(value);
                        }
                        break;
                }
            }
        }
        
        return task;
    }
    
    /**
     * 应用自定义参数
     */
    public void applyCustomParams(Map<String, Object> customParams) {
        if (customParams == null || customParams.isEmpty() || customizableParams == null) {
            return;
        }
        
        for (TaskTemplate.ConfigParam param : customizableParams) {
            if (!customParams.containsKey(param.getName())) {
                continue;
            }
            
            Object value = customParams.get(param.getName());
            
            // 根据参数类别应用配置
            switch (param.getCategory()) {
                case STDIO_ENV:
                    applyStdioEnvParam(param.getName(), String.valueOf(value));
                    break;
                case STDIO_ARG:
                    // 只处理布尔值，决定是否启用此参数
                    applyStdioArgParam(param, Boolean.parseBoolean(String.valueOf(value)));
                    break;
                case SSE_CONNECTION:
                    applySseConnectionParam(param.getName(), String.valueOf(value));
                    break;
            }
        }
        
        this.updatedAt = LocalDateTime.now();
    }
    
    private void applyStdioEnvParam(String name, String value) {
        if (connectionProfile != null && 
            connectionProfile.getConnectionType() == Constants.ConnectionType.STDIO &&
            connectionProfile.getStdioConfig() != null) {
                
            if (connectionProfile.getStdioConfig().getEnvironmentVars() == null) {
                connectionProfile.getStdioConfig().setEnvironmentVars(new HashMap<>());
            }
            
            connectionProfile.getStdioConfig().getEnvironmentVars().put(name, value);
        }
    }
    
    private void applyStdioArgParam(TaskTemplate.ConfigParam param, boolean enabled) {
        if (connectionProfile != null && 
            connectionProfile.getConnectionType() == Constants.ConnectionType.STDIO &&
            connectionProfile.getStdioConfig() != null) {
                
            if (connectionProfile.getStdioConfig().getCommandArgs() == null) {
                connectionProfile.getStdioConfig().setCommandArgs(new ArrayList<>());
            }
            
            // 获取存储在参数中的实际命令行参数值
            String argValue = param.getName();
            
            // 如果启用，添加到命令行参数
            if (enabled) {
                if (!connectionProfile.getStdioConfig().getCommandArgs().contains(argValue)) {
                    connectionProfile.getStdioConfig().getCommandArgs().add(argValue);
                }
            } else {
                // 如果禁用，从列表中移除
                connectionProfile.getStdioConfig().getCommandArgs().remove(argValue);
            }
        }
    }
    
    /**
     * 应用SSE连接参数
     */
    private void applySseConnectionParam(String name, String value) {
        if (connectionProfile != null && 
            connectionProfile.getConnectionType() == Constants.ConnectionType.SSE &&
            connectionProfile.getSseConfig() != null) {
                
            // 简化的SSE连接参数逻辑
            if ("serverUrl".equals(name)) {
                connectionProfile.getSseConfig().setServerUrl(value);
            } else if ("bearerToken".equals(name)) {
                connectionProfile.getSseConfig().setBearerToken(value);
            }
        }
    }
    
    /**
     * 检查特定参数是否为可定制参数
     */
    public boolean isCustomizableParam(String paramName) {
        if (this.customizableParams == null) {
            return false;
        }
        
        return this.customizableParams.stream()
                .anyMatch(param -> param.getName().equals(paramName));
    }
    
    /**
     * 根据名称获取可定制参数定义
     */
    public TaskTemplate.ConfigParam getConfigParam(String paramName) {
        if (this.customizableParams == null) {
            return null;
        }
        
        return this.customizableParams.stream()
                .filter(param -> param.getName().equals(paramName))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 根据类别获取所有可定制参数
     */
    public List<TaskTemplate.ConfigParam> getConfigParamsByCategory(TaskTemplate.ConfigParam.ConfigParamCategory category) {
        if (this.customizableParams == null) {
            return new ArrayList<>();
        }
        
        return this.customizableParams.stream()
                .filter(param -> param.getCategory() == category)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有可定制的环境变量参数
     */
    public List<TaskTemplate.ConfigParam> getEnvParams() {
        return getConfigParamsByCategory(TaskTemplate.ConfigParam.ConfigParamCategory.STDIO_ENV);
    }
    
    /**
     * 获取所有可定制的命令行参数
     */
    public List<TaskTemplate.ConfigParam> getArgParams() {
        return getConfigParamsByCategory(TaskTemplate.ConfigParam.ConfigParamCategory.STDIO_ARG);
    }
    
    /**
     * 获取所有SSE连接参数
     */
    public List<TaskTemplate.ConfigParam> getSseConnectionParams() {
        return getConfigParamsByCategory(TaskTemplate.ConfigParam.ConfigParamCategory.SSE_CONNECTION);
    }
    
    /**
     * 获取SSE服务器URL参数
     */
    public TaskTemplate.ConfigParam getServerUrlParam() {
        if (this.customizableParams == null) {
            return null;
        }
        
        return this.customizableParams.stream()
                .filter(param -> "serverUrl".equals(param.getName()) && 
                      param.getCategory() == TaskTemplate.ConfigParam.ConfigParamCategory.SSE_CONNECTION)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取Bearer Token参数
     */
    public TaskTemplate.ConfigParam getBearerTokenParam() {
        if (this.customizableParams == null) {
            return null;
        }
        
        return this.customizableParams.stream()
                .filter(param -> "bearerToken".equals(param.getName()) && 
                      param.getCategory() == TaskTemplate.ConfigParam.ConfigParamCategory.SSE_CONNECTION)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 将任务状态设为激活
     */
    public void activate() {
        this.status = TaskStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
        
        // 记录激活时间
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put("lastActivated", LocalDateTime.now().toString());
    }
    
    /**
     * 将任务状态设为非激活
     */
    public void deactivate() {
        this.status = TaskStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
        
        // 记录停用时间
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put("lastDeactivated", LocalDateTime.now().toString());
    }
    
    /**
     * 将任务状态设为错误
     */
    public void setError(String errorMessage) {
        this.status = TaskStatus.ERROR;
        this.updatedAt = LocalDateTime.now();
        
        // 记录错误信息
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        
        Map<String, String> error = new HashMap<>();
        error.put("message", errorMessage);
        error.put("timestamp", LocalDateTime.now().toString());
        
        this.metadata.put("lastError", error);
    }
    
    /**
     * 更新任务配置
     */
    public void updateConfig(Map<String, Object> updates) {
        if (this.config == null) {
            this.config = new HashMap<>();
        }
        this.config.putAll(updates);
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 获取连接状态信息
     */
    public Map<String, Object> getConnectionStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("taskId", this.id);
        status.put("taskName", this.name);
        status.put("status", this.status);
        status.put("connectionType", this.connectionProfile.getConnectionType());
        
        // 添加最近错误信息
        if (this.metadata != null && this.metadata.containsKey("lastError")) {
            status.put("lastError", this.metadata.get("lastError"));
        }
        
        return status;
    }
    
    // 辅助方法用于深拷贝ConnectionProfile
    private static ConnectionProfile deepCopy(ConnectionProfile original) {
        if (original == null) {
            return new ConnectionProfile();
        }
        
        ConnectionProfile copy = new ConnectionProfile();
        copy.setConnectionType(original.getConnectionType());
        copy.setConnectionTimeoutSeconds(original.getConnectionTimeoutSeconds());
        
        // 复制GeneralConfig
        GeneralConfig generalConfig = new GeneralConfig();
        if (original.getGeneralConfig() != null) {
            generalConfig.setTimeoutSeconds(original.getGeneralConfig().getTimeoutSeconds());
            generalConfig.setRetryCount(original.getGeneralConfig().getRetryCount());
            generalConfig.setLoggingLevel(original.getGeneralConfig().getLoggingLevel());
            
            if (original.getGeneralConfig().getCustomSettings() != null) {
                generalConfig.setCustomSettings(new HashMap<>(original.getGeneralConfig().getCustomSettings()));
            }
        }
        copy.setGeneralConfig(generalConfig);

        if (original.getConnectionType() == Constants.ConnectionType.STDIO && original.getStdioConfig() != null) {
            StdioConfig stdioConfig = new StdioConfig();
            stdioConfig.setCommand(original.getStdioConfig().getCommand());
            
            if (original.getStdioConfig().getCommandArgs() != null) {
                stdioConfig.setCommandArgs(new ArrayList<>(original.getStdioConfig().getCommandArgs()));
            }
            
            if (original.getStdioConfig().getEnvironmentVars() != null) {
                stdioConfig.setEnvironmentVars(new HashMap<>(original.getStdioConfig().getEnvironmentVars()));
            }
            
            copy.setStdioConfig(stdioConfig);
        } else if (original.getConnectionType() == Constants.ConnectionType.SSE && original.getSseConfig() != null) {
            SseConfig sseConfig = new SseConfig();
            sseConfig.setServerUrl(original.getSseConfig().getServerUrl());
            sseConfig.setBearerToken(original.getSseConfig().getBearerToken());
            
            copy.setSseConfig(sseConfig);
        }
        
        return copy;
    }
}