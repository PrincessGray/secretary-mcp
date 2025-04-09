// ConnectionProfile.java
package io.secretarymcp.model;

import io.secretarymcp.util.Constants;
import lombok.Data;

@Data
public class ConnectionProfile {
    // 使用Constants中的统一定义
    private Constants.ConnectionType connectionType = Constants.ConnectionType.SSE;
    private int connectionTimeoutSeconds = 60;
    
    // 新的配置对象
    private StdioConfig stdioConfig;
    private SseConfig sseConfig;
    
    // 通用配置
    private GeneralConfig generalConfig;
    
    /**
     * 获取STDIO配置，确保不为null
     */
    public StdioConfig getStdioConfig() {
        if (stdioConfig == null) {
            stdioConfig = new StdioConfig();
        }
        return stdioConfig;
    }
    
    /**
     * 获取SSE配置，确保不为null
     */
    public SseConfig getSseConfig() {
        if (sseConfig == null) {
            sseConfig = new SseConfig();
        }
        return sseConfig;
    }
    
    /**
     * 获取通用配置，确保不为null
     */
    public GeneralConfig getGeneralConfig() {
        if (generalConfig == null) {
            generalConfig = GeneralConfig.createDefault();
        }
        return generalConfig;
    }
    
    /**
     * 根据连接类型获取对应配置
     */
    public Object getTypeConfig() {
        return connectionType == Constants.ConnectionType.STDIO ? 
            getStdioConfig() : getSseConfig();
    }
}