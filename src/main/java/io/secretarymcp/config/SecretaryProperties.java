package io.secretarymcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * 秘书系统配置属性
 */
@Configuration
@ConfigurationProperties(prefix = "io.secretarymcp")
@Data
public class SecretaryProperties {
    
    private Storage storage = new Storage();
    private Mcp mcp = new Mcp();
    
    /**
     * 存储配置
     */
    @Data
    public static class Storage {
        /**
         * 数据存储基础目录
         */
        private String baseDir = "${user.home}/secretary-data";
    }
    
    /**
     * MCP协议配置
     */
    @Data
    public static class Mcp {
        private Client client = new Client();
        private Server server = new Server();
        
        /**
         * MCP客户端配置
         */
        @Data
        public static class Client {
            /**
             * 远程MCP服务器URL
             */
            private String url = "http://localhost:8081/mcp";
            
            /**
             * 连接超时时间（秒）
             */
            private int connectionTimeout = 30;
        }
        
        /**
         * MCP服务器配置
         */
        @Data
        public static class Server {
            /**
             * 本地MCP服务器端口
             */
            private int port = 8082;
        }
    }
}