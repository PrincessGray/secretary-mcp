package io.secretarymcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * 任务模板，定义任务的基本配置和连接方式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskTemplate {
    // 基本信息
    private String id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 连接配置
    private TaskType connectionType;  // 连接类型：SSE或STDIO
    private Map<String, Object> connectionParams;  // 连接参数
    
    // 可自定义参数描述
    private List<ParamDefinition> customizableParams;
    
    // 默认任务配置
    private Map<String, Object> defaultConfig;
    
    // 元数据
    private Map<String, Object> metadata;
    
    /**
     * 参数定义，描述可自定义的参数
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParamDefinition {
        private String name;               // 参数名称
        private String description;        // 参数描述
        private String type;               // 参数类型 (string, number, boolean, object, array)
        private Object defaultValue;       // 默认值
        private boolean required;          // 是否必需
        private List<Object> enumValues;   // 枚举值(如果适用)
    }
    
    /**
     * 创建一个新的任务模板实例
     */
    public static TaskTemplate create(String name, String description, TaskType connectionType) {
        return TaskTemplate.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .connectionType(connectionType)
                .connectionParams(new HashMap<>())
                .customizableParams(new ArrayList<>())
                .defaultConfig(new HashMap<>())
                .metadata(new HashMap<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 为SSE连接类型添加连接参数
     */
    public void configureSseConnection(String serverUrl) {
        if (this.connectionType != TaskType.SSE) {
            throw new IllegalStateException("只能为SSE类型的任务配置SSE连接参数");
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("serverUrl", serverUrl);  // 唯一必需的参数
        
        this.connectionParams = params;
        
        // 设置可自定义参数
        List<ParamDefinition> customParams = new ArrayList<>();
        
        // 添加认证令牌参数
        customParams.add(ParamDefinition.builder()
                .name("authToken")
                .description("认证令牌，用于连接到SSE服务器")
                .type("string")
                .defaultValue("")  // 空字符串作为默认值
                .required(false)
                .build());
                
        // 添加自定义头部参数
        customParams.add(ParamDefinition.builder()
                .name("customHeaders")
                .description("自定义HTTP头部，格式为JSON对象，每个键值对代表一个头部")
                .type("object")
                .defaultValue(new HashMap<String, String>())  // 空Map作为默认值
                .required(false)
                .build());
                
        // 添加连接超时参数
        customParams.add(ParamDefinition.builder()
                .name("connectionTimeoutSeconds")
                .description("连接超时时间（秒）")
                .type("number")
                .defaultValue(60)  // 60秒作为默认值
                .required(false)
                .build());
                
        this.customizableParams = customParams;
        
        // 添加默认配置
        setDefaultConfigValue("enableRoots", false);
        setDefaultConfigValue("enableSampling", false);
    }
    
    /**
     * 为STDIO连接类型添加连接参数
     */
    public void configureStdioConnection(String command) {
        if (this.connectionType != TaskType.STDIO) {
            throw new IllegalStateException("只能为STDIO类型的任务配置STDIO连接参数");
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("command", command);
        
        this.connectionParams = params;
        
        // 设置可自定义参数
        List<ParamDefinition> customParams = new ArrayList<>();
        
        // 添加环境变量参数
        customParams.add(ParamDefinition.builder()
                .name("environmentVars")
                .description("环境变量，格式为JSON对象，每个键值对代表一个环境变量")
                .type("object")
                .defaultValue(new HashMap<String, String>())
                .required(false)
                .build());
                
        // 添加命令参数
        customParams.add(ParamDefinition.builder()
                .name("commandArgs")
                .description("命令行参数，格式为字符串数组")
                .type("array")
                .defaultValue(new ArrayList<String>())
                .required(false)
                .build());
                
        // 添加工作目录参数
        customParams.add(ParamDefinition.builder()
                .name("workingDir")
                .description("命令执行的工作目录路径")
                .type("string")
                .defaultValue("")
                .required(false)
                .build());
                
        // 添加连接超时参数
        customParams.add(ParamDefinition.builder()
                .name("connectionTimeoutSeconds")
                .description("连接超时时间（秒）")
                .type("number")
                .defaultValue(60)
                .required(false)
                .build());
                
        this.customizableParams = customParams;
        
        // 添加默认配置
        setDefaultConfigValue("enableRoots", false);
        setDefaultConfigValue("enableSampling", false);
    }
    
    /**
     * 添加自定义参数定义
     */
    public void addCustomizableParam(ParamDefinition paramDefinition) {
        if (this.customizableParams == null) {
            this.customizableParams = new ArrayList<>();
        }
        this.customizableParams.add(paramDefinition);
    }
    
    /**
     * 设置默认配置
     */
    public void setDefaultConfigValue(String key, Object value) {
        if (this.defaultConfig == null) {
            this.defaultConfig = new HashMap<>();
        }
        this.defaultConfig.put(key, value);
    }
    
    /**
     * 获取完整的默认配置
     */
    public Map<String, Object> getDefaultConfig() {
        return defaultConfig != null ? new HashMap<>(defaultConfig) : new HashMap<>();
    }
    
    /**
     * 获取完整的连接参数
     */
    public Map<String, Object> getConnectionParams() {
        return connectionParams != null ? new HashMap<>(connectionParams) : new HashMap<>();
    }

    
}