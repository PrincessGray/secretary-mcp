package io.secretarymcp.proxy;

import io.secretarymcp.proxy.client.UpstreamClientConfig;
import io.secretarymcp.proxy.server.SseProxyServer;
import io.secretarymcp.proxy.server.StdioProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;

/**
 * 代理服务器适配器
 * 将代理服务器实现适配到ProxyServer接口
 */
@Component
public class ProxyServerAdapter implements ProxyServer {
    private static final Logger log = LoggerFactory.getLogger(ProxyServerAdapter.class);
    
    private final ProxyServer delegateServer;
    
    /**
     * 构造函数，根据当前环境自动选择合适的代理服务器实现
     */
    public ProxyServerAdapter(
            @Autowired(required = false) SseProxyServer sseProxyServer,
            @Autowired(required = false) StdioProxyServer stdioProxyServer,
            Environment environment) {
        
        boolean sseMode = Arrays.asList(environment.getActiveProfiles()).contains("sse");
        
        if (sseMode && sseProxyServer != null) {
            log.info("使用SSE代理服务器");
            this.delegateServer = new SseProxyServerWrapper(sseProxyServer);
        } else if (stdioProxyServer != null) {
            log.info("使用STDIO代理服务器");
            this.delegateServer = new StdioProxyServerWrapper(stdioProxyServer);
        } else {
            log.error("没有可用的代理服务器实现");
            throw new IllegalStateException("没有可用的代理服务器实现");
        }
    }
    
    @Override
    public Mono<Void> initialize() {
        return delegateServer.initialize();
    }
    
    @Override
    public Mono<Void> addUpstreamClient(UpstreamClientConfig config) {
        return delegateServer.addUpstreamClient(config);
    }
    
    @Override
    public Mono<Void> removeUpstreamClient(String taskId) {
        return delegateServer.removeUpstreamClient(taskId);
    }
    
    @Override
    public Mono<Void> shutdown() {
        return delegateServer.shutdown();
    }

    /**
         * SseProxyServer包装器，实现ProxyServer接口
         */
        private record SseProxyServerWrapper(SseProxyServer sseProxyServer) implements ProxyServer {

        @Override
            public Mono<Void> initialize() {
                return sseProxyServer.initialize();
            }

        @Override
            public Mono<Void> addUpstreamClient(UpstreamClientConfig config) {
                return sseProxyServer.addUpstreamClient(config);
            }

        @Override
            public Mono<Void> removeUpstreamClient(String taskId) {
                return sseProxyServer.removeUpstreamClient(taskId);
            }

        @Override
            public Mono<Void> shutdown() {
                return sseProxyServer.shutdown();
            }
        }

    /**
         * StdioProxyServer包装器，实现ProxyServer接口
         */
        private record StdioProxyServerWrapper(StdioProxyServer stdioProxyServer) implements ProxyServer {

        @Override
            public Mono<Void> initialize() {
                return stdioProxyServer.initialize();
            }

        @Override
            public Mono<Void> addUpstreamClient(UpstreamClientConfig config) {
                return stdioProxyServer.addUpstreamClient(config);
            }

        @Override
            public Mono<Void> removeUpstreamClient(String taskId) {
                return stdioProxyServer.removeUpstreamClient(taskId);
            }

        @Override
            public Mono<Void> shutdown() {
                return stdioProxyServer.shutdown();
            }
        }
}