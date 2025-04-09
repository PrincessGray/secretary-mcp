package io.secretarymcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.secretarymcp.config.SecretaryProperties;
import io.secretarymcp.proxy.server.StdioProxyServer;
import io.secretarymcp.storage.FileSystemStorage;
import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * MCP秘书系统应用程序入口
 */
@SpringBootApplication
@EnableConfigurationProperties(SecretaryProperties.class)
@Slf4j
public class SecretaryApplication {

    private final FileSystemStorage storage;
    private final StdioProxyServer proxyServer;
    private final Environment environment;
    @Getter
    private final ObjectMapper objectMapper;
    
    // 用于阻塞主线程的锁
    private static CountDownLatch exitLatch;

    // 构造函数注入依赖
    public SecretaryApplication(FileSystemStorage storage, StdioProxyServer proxyServer, 
                              Environment environment, ObjectMapper objectMapper) {
        this.storage = storage;
        this.proxyServer = proxyServer;
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    public static void main(String[] args) {
        // 检查是否以stdio模式启动
        boolean stdioMode = Arrays.asList(args).contains("--stdio");
        
        // 创建应用上下文
        ConfigurableApplicationContext context = SpringApplication.run(SecretaryApplication.class, args);
        
        if (!stdioMode) {
            log.info("以stdio模式启动");
            
            // 获取应用实例
            SecretaryApplication app = context.getBean(SecretaryApplication.class);
            
            // 创建退出锁
            exitLatch = new CountDownLatch(1);
            
            // 启动代理服务器并阻塞主线程
            app.startProxyServerInStdioMode();
            
            try {
                // 等待退出信号
                exitLatch.await();
                log.info("接收到退出信号，准备关闭应用");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("主线程等待被中断");
            } finally {
                // 确保应用正常关闭
                SpringApplication.exit(context);
            }
        } else {
            log.info("以API模式启动");
            // API模式下无需额外操作，SpringBoot会自动运行
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
    }
    
    /**
     * 启动代理服务器处理stdio
     */
    private void startProxyServerInStdioMode() {
        try {
            log.info("正在初始化STDIO代理服务器");
            
            // 初始化代理服务器并运行
            proxyServer.initialize()
                .doOnSuccess(v -> log.info("STDIO代理服务器初始化成功"))
                .then(Mono.defer(() -> {
                    log.info("STDIO代理服务器开始运行...");
                    return proxyServer.run();
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

    @PreDestroy
    public void onShutdown() {
        log.info("MCP秘书系统关闭中");

        // 关闭代理服务器
        if (proxyServer != null) {
            proxyServer.shutdown()
                    .doOnSuccess(v -> log.info("代理服务器已关闭"))
                    .doOnError(e -> log.error("代理服务器关闭出错: {}", e.getMessage(), e))
                    .block(Duration.ofSeconds(10));
        }
        
        // 释放退出锁，让主线程可以退出
        if (exitLatch != null) {
            exitLatch.countDown();
        }
    }
}