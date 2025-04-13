package io.secretarymcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * MCP相关配置类
 */
@Configuration
public class McpConfig {

    /**
     * 创建一个SSE服务器传输提供者
     * 
     * @param objectMapper JSON对象映射器
     * @param sseEndpoint SSE端点路径
     * @return WebFluxSseServerTransportProvider实例
     */
    @Bean
    @Profile("sse")
    public WebFluxSseServerTransportProvider webFluxSseServerTransportProvider(
            ObjectMapper objectMapper,
            @Value("${mcp.sse.endpoint:/sse}") String sseEndpoint) {
        return new WebFluxSseServerTransportProvider(objectMapper, sseEndpoint);
    }

    /**
     * 提供SSE路由函数
     * 
     * @param transportProvider SSE传输提供者
     * @return 路由函数
     */
    @Bean
    @Profile("sse")
    public RouterFunction<ServerResponse> sseRouterFunction(WebFluxSseServerTransportProvider transportProvider) {
        @SuppressWarnings("unchecked")
        RouterFunction<ServerResponse> typedRouterFunction = 
            (RouterFunction<ServerResponse>) transportProvider.getRouterFunction();
        return typedRouterFunction;
    }
} 