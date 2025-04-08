package io.secretarymcp.proxy;

import io.secretarymcp.proxy.client.UpstreamClientConfig;
import reactor.core.publisher.Mono;

/**
 * 代理服务器接口
 * 定义代理服务器的基本操作
 */
public interface ProxyServer {
    /**
     * 初始化代理服务器
     * @return 操作结果
     */
    Mono<Void> initialize();
    
    /**
     * 添加上游客户端
     * @param config 上游客户端配置
     * @return 操作结果
     */
    Mono<Void> addUpstreamClient(UpstreamClientConfig config);
    
    /**
     * 移除上游客户端
     * @param taskId 任务ID
     * @return 操作结果
     */
    Mono<Void> removeUpstreamClient(String taskId);
    
    /**
     * 关闭代理服务器
     * @return 操作结果
     */
    Mono<Void> shutdown();
}