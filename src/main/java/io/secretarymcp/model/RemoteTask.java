package io.secretarymcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.secretarymcp.util.Constants.TaskStatus;
import io.secretarymcp.util.Constants.TaskType;
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
    private String templateId;
    private TaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 连接配置
    private TaskType connectionType;
    private Map<String, Object> connectionParams;
    
    // 可自定义参数描述 (从模板继承)
    private List<TaskTemplate.ParamDefinition> customizableParams;
    
    // 任务配置
    private Map<String, Object> config;
    
    // 元数据
    private Map<String, Object> metadata;
    
    /**
     * 从模板创建任务
     */
    public static RemoteTask fromTemplate(TaskTemplate template, String secretaryId, String name) {
        String taskName = name != null && !name.isBlank() ? name : template.getName();
        
        return RemoteTask.builder()
                .id(UUID.randomUUID().toString())
                .name(taskName)
                .description(template.getDescription())
                .secretaryId(secretaryId)
                .templateId(template.getId())
                .status(TaskStatus.INACTIVE)
                .connectionType(template.getConnectionType())
                .connectionParams(new HashMap<>(template.getConnectionParams()))
                .customizableParams(new ArrayList<>(template.getCustomizableParams()))
                .config(new HashMap<>(template.getDefaultConfig()))
                .metadata(new HashMap<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 应用参数配置到连接参数
     * 只有在customizableParams中定义的参数才会被应用
     */
    public void applyCustomConnectionParams(Map<String, Object> customParams) {
        if (customParams == null || customParams.isEmpty()) {
            return;
        }
        
        if (this.connectionParams == null) {
            this.connectionParams = new HashMap<>();
        }
        
        // 只应用定义为可自定义的参数
        if (this.customizableParams != null) {
            for (TaskTemplate.ParamDefinition paramDef : this.customizableParams) {
                if (customParams.containsKey(paramDef.getName())) {
                    this.connectionParams.put(paramDef.getName(), customParams.get(paramDef.getName()));
                }
            }
        }
        
        this.updatedAt = LocalDateTime.now();
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
     * 获取完整的配置
     */
    public Map<String, Object> getConfig() {
        return config != null ? new HashMap<>(config) : new HashMap<>();
    }
    
    /**
     * 获取完整的连接参数
     */
    public Map<String, Object> getConnectionParams() {
        return connectionParams != null ? new HashMap<>(connectionParams) : new HashMap<>();
    }
    
    /**
     * 检查参数是否为可自定义参数
     */
    public boolean isCustomizableParam(String paramName) {
        if (this.customizableParams == null) {
            return false;
        }
        
        return this.customizableParams.stream()
                .anyMatch(param -> param.getName().equals(paramName));
    }
    
    /**
     * 获取可自定义参数的描述
     */
    public TaskTemplate.ParamDefinition getParamDefinition(String paramName) {
        if (this.customizableParams == null) {
            return null;
        }
        
        return this.customizableParams.stream()
                .filter(param -> param.getName().equals(paramName))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取连接状态信息
     */
    public Map<String, Object> getConnectionStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("taskId", this.id);
        status.put("taskName", this.name);
        status.put("status", this.status);
        status.put("connectionType", this.connectionType);
        
        // 添加最近错误信息
        if (this.metadata != null && this.metadata.containsKey("lastError")) {
            status.put("lastError", this.metadata.get("lastError"));
        }
        
        return status;
    }
}