package io.secretarymcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用任务配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralConfig {
    // 执行控制
    private Integer timeoutSeconds;
    private Integer retryCount;
    private Integer retryDelaySeconds;
    
    // MCP功能选项
    private Boolean enableRoots;
    private Boolean enableSampling;
    private String loggingLevel;
    
    // 资源限制
    private Long maxMemoryMb;
    private Integer maxCpuPercent;
    private Long maxDiskSpaceMb;
    
    // 行为控制
    private Boolean verboseMode;
    private Boolean enableDryRun;
    private String outputFormat;
    
    // 其他自定义配置 - 可用于存储不在标准字段中的配置
    private java.util.Map<String, Object> customSettings;
    
    /**
     * 获取自定义设置
     */
    public Object getCustomSetting(String key) {
        return customSettings != null ? customSettings.get(key) : null;
    }
    
    /**
     * 设置自定义设置
     */
    public void setCustomSetting(String key, Object value) {
        if (customSettings == null) {
            customSettings = new java.util.HashMap<>();
        }
        customSettings.put(key, value);
    }
    
    /**
     * 检查是否存在某个自定义设置
     */
    public boolean hasCustomSetting(String key) {
        return customSettings != null && customSettings.containsKey(key);
    }
    
    /**
     * 创建一个基本的通用配置
     */
    public static GeneralConfig createDefault() {
        return GeneralConfig.builder()
                .timeoutSeconds(60)
                .retryCount(0)
                .enableRoots(false)
                .enableSampling(false)
                .loggingLevel("info")
                .verboseMode(false)
                .build();
    }
}