package io.secretarymcp.proxy;

import io.secretarymcp.proxy.client.UpstreamClientConfig;
import io.secretarymcp.proxy.server.StdioProxyServer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 代理服务器适配器
 * 将StdioProxyServer适配到ProxyServer接口
 */
@Component
@RequiredArgsConstructor
public class ProxyServerAdapter implements ProxyServer {

    private final StdioProxyServer stdioProxyServer;
    
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