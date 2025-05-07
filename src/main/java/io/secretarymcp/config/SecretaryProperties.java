package io.secretarymcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.Getter;
/**
 * 秘书系统配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "io.secretarymcp")
@Data
public class SecretaryProperties {
    
    private Storage storage = new Storage();
    //private Mcp mcp = new Mcp();
    
    /**
     * 存储配置
     */
    @Getter
    @Data
    public static class Storage {
        /**
         * 数据存储基础目录
         */
        private String baseDir = "${user.home}/secretary-data";
    }
}