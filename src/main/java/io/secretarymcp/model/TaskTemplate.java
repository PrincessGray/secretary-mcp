package io.secretarymcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.secretarymcp.util.Constants;
import io.secretarymcp.util.Constants.ManagementType;
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
    
    // 使用统一的ConnectionProfile
    private ConnectionProfile connectionProfile;
    
    // 可定制配置项标记
    private List<ConfigParam> customizableParams;
    
    // 默认任务配置
    private Map<String, Object> defaultConfig;
    
    // 元数据
    private Map<String, Object> metadata;

    /**
     * 配置参数定义，标识可定制的参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigParam {

        private String name;           // 参数名称
        private String displayName;    // 显示名称
        private String description;    // 参数描述
        private String type;           // 参数类型 (string, number, boolean等)
        private boolean required;      // 是否必填
        private Object defaultValue;   // 默认值
        private ConfigParamCategory category; // 参数类别
        
        // 参数所属类别
        public enum ConfigParamCategory {
            STDIO_ENV,        // STDIO环境变量
            STDIO_ARG,        // STDIO命令行参数(只允许选择是否启用)
            SSE_CONNECTION    // SSE连接参数(URL和bearerToken)
        }
    }
    
    /**
     * 创建一个新的任务模板实例
     */
    public static TaskTemplate create(String name, String description, Constants.ConnectionType connectionType) {
        TaskTemplate template = TaskTemplate.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .customizableParams(new ArrayList<>())
                .defaultConfig(new HashMap<>())
                .metadata(new HashMap<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        // 初始化连接配置
        template.setConnectionProfile(new ConnectionProfile());
        template.getConnectionProfile().setConnectionType(connectionType);
        
        // 根据连接类型初始化特定配置
        if (connectionType == Constants.ConnectionType.STDIO) {
            template.getConnectionProfile().setStdioConfig(new StdioConfig());
        } else {
            template.getConnectionProfile().setSseConfig(new SseConfig());
        }
        
        return template;
    }

    /**
     * 创建SSE连接模板
     */
    public static TaskTemplate createSseTemplate(String name, String description, String serverUrl) {
        TaskTemplate template = create(name, description, Constants.ConnectionType.SSE);
        
        // 配置SSE连接
        SseConfig sseConfig = template.getConnectionProfile().getSseConfig();
        sseConfig.setServerUrl(serverUrl);
        
        // 添加默认配置
        template.setDefaultConfigValue("enableRoots", false);
        template.setDefaultConfigValue("enableSampling", false);
        
        // 添加SSE连接参数（URL和bearerToken）
        template.addSseUrlParam(serverUrl, true);
        template.addSseBearerTokenParam("", false);
        
        return template;
    }
    
    /**
     * 创建STDIO连接模板
     */
    public static TaskTemplate createStdioTemplate(String name, String description, String command, ManagementType managementType) {
        TaskTemplate template = create(name, description, Constants.ConnectionType.STDIO);
        
        // 配置STDIO连接
        StdioConfig stdioConfig = template.getConnectionProfile().getStdioConfig();
        stdioConfig.setCommand(command);
        stdioConfig.setCommandArgs(new ArrayList<>());
        stdioConfig.setEnvironmentVars(new HashMap<>());
        stdioConfig.setManagementType(managementType != null ? managementType : ManagementType.NPX);
        
        // 添加默认配置
        template.setDefaultConfigValue("enableRoots", false);
        template.setDefaultConfigValue("enableSampling", false);
        
        return template;
    }
    
    /**
     * 重载的创建STDIO连接模板方法（向后兼容）
     */
    public static TaskTemplate createStdioTemplate(String name, String description, String command) {
        return createStdioTemplate(name, description, command, ManagementType.NPX);
    }
    
    /**
     * 添加可定制的环境变量参数 (STDIO)
     */
    public void addCustomizableEnvParam(String name, String displayName, String description, String defaultValue, boolean required) {
        if (getConnectionProfile().getConnectionType() != Constants.ConnectionType.STDIO) {
            throw new IllegalStateException("只有STDIO连接类型支持环境变量参数");
        }
        
        ConfigParam param = ConfigParam.builder()
                .name(name)
                .displayName(displayName)
                .description(description)
                .type("string")
                .defaultValue(defaultValue)
                .required(required)
                .category(ConfigParam.ConfigParamCategory.STDIO_ENV)
                .build();
                
        getCustomizableParams().add(param);
        
        // 如果有默认值，设置到环境变量中
        if (defaultValue != null) {
            StdioConfig stdioConfig = getConnectionProfile().getStdioConfig();
            if (stdioConfig.getEnvironmentVars() == null) {
                stdioConfig.setEnvironmentVars(new HashMap<>());
            }
            stdioConfig.getEnvironmentVars().put(name, defaultValue);
        }
    }
    
    /**
     * 添加可定制的命令行参数 (STDIO)，只允许选择是否启用
     */
    public void addCustomizableArgParam(String name, String displayName, String description, boolean enabledByDefault, boolean required) {
        if (getConnectionProfile().getConnectionType() != Constants.ConnectionType.STDIO) {
            throw new IllegalStateException("只有STDIO连接类型支持命令行参数");
        }
        
        ConfigParam param = ConfigParam.builder()
                .name(name)
                .displayName(displayName)
                .description(description)
                .type("boolean")
                .defaultValue(enabledByDefault)
                .required(required)
                .category(ConfigParam.ConfigParamCategory.STDIO_ARG)
                .build();
        
        getCustomizableParams().add(param);
        
        // 如果默认启用，添加到命令行参数列表
        if (enabledByDefault) {
            StdioConfig stdioConfig = getConnectionProfile().getStdioConfig();
            if (stdioConfig.getCommandArgs() == null) {
                stdioConfig.setCommandArgs(new ArrayList<>());
            }
            stdioConfig.getCommandArgs().add(name);
        }
    }
    
    /**
     * 添加SSE URL参数
     */
    public void addSseUrlParam(String defaultValue, boolean required) {
        if (getConnectionProfile().getConnectionType() != Constants.ConnectionType.SSE) {
            throw new IllegalStateException("只有SSE连接类型支持URL参数");
        }
        
        ConfigParam param = ConfigParam.builder()
                .name("serverUrl")
                .displayName("服务器URL")
                .description("SSE服务器的URL地址")
                .type("string")
                .defaultValue(defaultValue)
                .required(required)
                .category(ConfigParam.ConfigParamCategory.SSE_CONNECTION)
                .build();
        
        getCustomizableParams().add(param);
        
        // 设置默认值
        if (defaultValue != null) {
            SseConfig sseConfig = getConnectionProfile().getSseConfig();
            sseConfig.setServerUrl(defaultValue);
        }
    }
    
    /**
     * 添加SSE Bearer Token参数
     */
    public void addSseBearerTokenParam(String defaultValue, boolean required) {
        if (getConnectionProfile().getConnectionType() != Constants.ConnectionType.SSE) {
            throw new IllegalStateException("只有SSE连接类型支持Bearer Token参数");
        }
        
        ConfigParam param = ConfigParam.builder()
                .name("bearerToken")
                .displayName("Bearer Token")
                .description("用于SSE服务器认证的Bearer Token")
                .type("string")
                .defaultValue(defaultValue)
                .required(required)
                .category(ConfigParam.ConfigParamCategory.SSE_CONNECTION)
                .build();
        
        getCustomizableParams().add(param);
        
        // 设置默认值
        if (defaultValue != null) {
            SseConfig sseConfig = getConnectionProfile().getSseConfig();
            sseConfig.setBearerToken(defaultValue);
        }
    }
    
    // 添加辅助方法来设置标准GeneralConfig字段
    private boolean setStandardGeneralConfigField(String name, Object value) {
        GeneralConfig config = getConnectionProfile().getGeneralConfig();
        
        switch (name) {
            case "timeoutSeconds":
                if (value instanceof Number) {
                    config.setTimeoutSeconds(((Number) value).intValue());
                    return true;
                }
                break;
            case "retryCount":
                if (value instanceof Number) {
                    config.setRetryCount(((Number) value).intValue());
                    return true;
                }
                break;
            case "enableRoots":
                if (value instanceof Boolean) {
                    config.setEnableRoots((Boolean) value);
                    return true;
                }
                break;
            case "enableSampling":
                if (value instanceof Boolean) {
                    config.setEnableSampling((Boolean) value);
                    return true;
                }
                break;
            case "loggingLevel":
                if (value instanceof String) {
                    config.setLoggingLevel((String) value);
                    return true;
                }
                break;
            // 添加其他标准字段...
        }
        
        return false; // 不是标准字段
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
     * 确保customizableParams初始化
     */
    public List<ConfigParam> getCustomizableParams() {
        if (customizableParams == null) {
            customizableParams = new ArrayList<>();
        }
        return customizableParams;
    }
}