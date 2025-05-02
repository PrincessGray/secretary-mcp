package io.secretarymcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.secretarymcp.config.SecretaryProperties;
import io.secretarymcp.proxy.server.SseProxyServer;
import io.secretarymcp.proxy.server.StdioProxyServer;
import io.secretarymcp.storage.FileSystemStorage;
import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Hooks;
import java.util.Properties;
import java.io.InputStream;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import io.secretarymcp.registry.UserSecretaryRegistry;

/**
 * MCP秘书系统应用程序入口
 */
@SpringBootApplication
@EnableConfigurationProperties(SecretaryProperties.class)
@Slf4j
public class SecretaryApplication {

    private final FileSystemStorage storage;
    private final StdioProxyServer stdioProxyServer;
    private final SseProxyServer sseProxyServer;
    private final Environment environment;
    @Getter
    private final ObjectMapper objectMapper;
    
    // 添加UserSecretaryRegistry
    private final UserSecretaryRegistry userSecretaryRegistry;
    
    // 用于阻塞主线程的锁
    private static CountDownLatch exitLatch;

    // 构造函数注入依赖，添加UserSecretaryRegistry
    public SecretaryApplication(FileSystemStorage storage, 
                              StdioProxyServer stdioProxyServer,
                              SseProxyServer sseProxyServer,
                              Environment environment, 
                              ObjectMapper objectMapper,
                              UserSecretaryRegistry userSecretaryRegistry) {
        this.storage = storage;
        this.stdioProxyServer = stdioProxyServer;
        this.sseProxyServer = sseProxyServer;
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.userSecretaryRegistry = userSecretaryRegistry;
    }

    static {
        Hooks.onErrorDropped(e -> {
            log.error("捕获到未处理的响应式错误: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("错误详情:", e);
            }
        });
    }

    public static void main(String[] args) {
        // 检查启动模式
        boolean stdioMode = Arrays.asList(args).contains("--stdio");
        
        // 创建Spring应用
        SpringApplication app = new SpringApplication(SecretaryApplication.class);
        
        // 只处理STDIO模式，默认就是SSE模式(WebFlux)
        if (stdioMode) {
            app.setAdditionalProfiles("stdio");
            app.setWebApplicationType(WebApplicationType.NONE);
        } else {
            // 默认使用reactive WebFlux
            app.setWebApplicationType(WebApplicationType.REACTIVE);
        }
        
        // 运行应用
        ConfigurableApplicationContext context = app.run(args);
        
        // STDIO模式需要特殊处理
        if (stdioMode) {
            // 创建退出锁
            exitLatch = new CountDownLatch(1);
            try {
                exitLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                SpringApplication.exit(context);
            }
        }
    }

    /**
     * 应用程序就绪事件监听器
     * 当应用启动完成后执行初始化操作
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("MCP秘书系统启动就绪");

        // 初始化存储系统
        storage.initialize()
                .doOnSuccess(v -> log.info("存储系统初始化完成"))
                .doOnError(e -> log.error("存储系统初始化失败: {}", e.getMessage(), e))
                .block();
                
        // 如果是SSE模式，初始化并启动SSE代理服务器
        boolean sseMode = Arrays.asList(environment.getActiveProfiles()).contains("sse") || 
                          "sse".equalsIgnoreCase(environment.getProperty("mcp.transport"));
                          
        if (sseMode && !Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            log.info("API模式下初始化SSE代理服务器");
            
            // 先加载所有Secretary信息，以便注册给代理服务器
            loadAndPreRegisterSecretaries()
                .then(Mono.defer(() -> {
                    // 确保只初始化一次
                    return sseProxyServer.isHealthy()
                        .flatMap(healthy -> {
                            if (healthy) {
                                log.info("SSE代理服务器已初始化，无需重复初始化");
                                return Mono.empty();
                            }
                            
                            log.info("SSE代理服务器尚未初始化，开始初始化...");
                            return sseProxyServer.initialize()
                                    .doOnSuccess(v -> log.info("SSE代理服务器初始化成功"))
                                    .doOnError(e -> log.error("SSE代理服务器初始化失败: {}", e.getMessage(), e));
                        })
                        .then(Mono.defer(() -> {
                            log.info("SSE代理服务器开始运行...");
                            return sseProxyServer.run();
                        }));
                }))
                .doOnError(e -> log.error("SSE代理服务器运行出错: {}", e.getMessage(), e))
                .subscribe(); // 使用subscribe而不是block，避免阻塞事件循环
        }
    }
    
    /**
     * 启动代理服务器处理stdio
     */
    private void startProxyServerInStdioMode() {
        try {
            log.info("正在初始化STDIO代理服务器");
            
            // 初始化代理服务器并运行
            stdioProxyServer.initialize()
                .doOnSuccess(v -> log.info("STDIO代理服务器初始化成功"))
                .then(Mono.defer(() -> {
                    log.info("STDIO代理服务器开始运行...");
                    return stdioProxyServer.run();
                }))
                .doOnError(e -> {
                    log.error("STDIO代理服务器运行出错: {}", e.getMessage(), e);
                    exitLatch.countDown(); // 发生错误时释放锁
                })
                .block(); // 阻塞直到完成或出错
                
            // 由于proxyServer.run()返回Mono.never()，这里通常不会执行
            log.info("STDIO代理服务器已退出");
            exitLatch.countDown();
        } catch (Exception e) {
            log.error("启动STDIO代理服务器失败: {}", e.getMessage(), e);
            exitLatch.countDown(); // 发生异常时释放锁
        }
    }
    
    /**
     * 启动代理服务器处理SSE
     */
    private void startProxyServerInSseMode() {
        try {
            log.info("正在初始化SSE代理服务器");
            
            // 初始化代理服务器并运行，传递默认秘书名称
            // 如果有需要，可以从配置中读取默认秘书名称
            String defaultSecretaryName = environment.getProperty("mcp.default-secretary", "default");
            
            sseProxyServer.initialize()
                .doOnSuccess(v -> log.info("SSE代理服务器初始化成功"))
                .then(Mono.defer(() -> {
                    log.info("SSE代理服务器开始运行...");
                    return sseProxyServer.run();
                }))
                .doOnError(e -> {
                    log.error("SSE代理服务器运行出错: {}", e.getMessage(), e);
                    exitLatch.countDown(); // 发生错误时释放锁
                })
                .block(); // 阻塞直到完成或出错
                
            // 由于proxyServer.run()返回Mono.never()，这里通常不会执行
            log.info("SSE代理服务器已退出");
            exitLatch.countDown();
        } catch (Exception e) {
            log.error("启动SSE代理服务器失败: {}", e.getMessage(), e);
            exitLatch.countDown(); // 发生异常时释放锁
        }
    }

    @PreDestroy
    public void onShutdown() {
        log.info("MCP秘书系统关闭中");

        // 关闭代理服务器
        if (stdioProxyServer != null) {
            stdioProxyServer.shutdown()
                    .doOnSuccess(v -> log.info("STDIO代理服务器已关闭"))
                    .doOnError(e -> log.error("STDIO代理服务器关闭出错: {}", e.getMessage(), e))
                    .block(Duration.ofSeconds(10));
        }
        
        // 关闭SSE代理服务器
        if (sseProxyServer != null) {
            sseProxyServer.shutdown()
                    .doOnSuccess(v -> log.info("SSE代理服务器已关闭"))
                    .doOnError(e -> log.error("SSE代理服务器关闭出错: {}", e.getMessage(), e))
                    .block(Duration.ofSeconds(10));
        }
        
        // 释放退出锁，让主线程可以退出
        if (exitLatch != null) {
            exitLatch.countDown();
        }
    }

    /**
     * 加载并预注册所有秘书信息
     * 这样，当代理服务器初始化时，就可以使用正确的秘书名称来命名工具
     */
    private Mono<Void> loadAndPreRegisterSecretaries() {
        return storage.listSecretaryInfos()
            .doOnNext(secretaryInfo -> {
                log.info("加载秘书信息: {} ({})", secretaryInfo.getName(), secretaryInfo.getId());
                
                // 将秘书注册到相关组件中
                // 例如可以预先将秘书信息注册到工具管理器中，以便正确构建工具名称
                // 这里我们假设只需要记录秘书信息，实际实现可能需要根据您的需求调整
            })
            .then();
    }

    /**
     * 添加 CORS 配置允许前端访问
     */
    @Configuration
    public static class WebConfig implements WebFluxConfigurer {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000") // 允许前端开发服务器访问
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true);
        }
    }
    
}