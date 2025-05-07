package io.secretarymcp.web;

import io.secretarymcp.proxy.server.SseProxyServer;
import io.secretarymcp.registry.UserSecretaryRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-secretary")
public class UserSecretaryController {
    private final SseProxyServer sseProxyServer;
    private final UserSecretaryRegistry userSecretaryRegistry;
    
    @Autowired
    public UserSecretaryController(SseProxyServer sseProxyServer, UserSecretaryRegistry userSecretaryRegistry) {
        this.sseProxyServer = sseProxyServer;
        this.userSecretaryRegistry = userSecretaryRegistry;
    }
    
    @PostMapping("/register")
    public Mono<ResponseEntity<Void>> registerUserSecretary(
            @RequestParam String userId,
            @RequestParam String secretaryName) {
        // 确保服务器已初始化
        return sseProxyServer.initialize()
            .then(sseProxyServer.getMcpServer().registerUserSecretary(userId, secretaryName))
            .thenReturn(ResponseEntity.ok().build());
    }
    
    @DeleteMapping("/unregister")
    public Mono<ResponseEntity<Void>> unregisterUser(
            @RequestParam String userId) {
        return sseProxyServer.initialize()
            .then(sseProxyServer.getMcpServer().unregisterUser(userId))
            .thenReturn(ResponseEntity.ok().build());
    }
    
    @DeleteMapping("/unregister-specific")
    public Mono<ResponseEntity<Void>> unregisterUserSecretary(
            @RequestParam String userId,
            @RequestParam String secretaryName) {
        return sseProxyServer.initialize()
            .then(sseProxyServer.getMcpServer().unregisterUserSecretary(userId, secretaryName))
            .thenReturn(ResponseEntity.ok().build());
    }
    
    @GetMapping("/secretary")
    public Mono<ResponseEntity<String>> getSecretaryForUser(
            @RequestParam String userId) {
        return sseProxyServer.initialize()
            .then(sseProxyServer.getMcpServer().getSecretaryForUser(userId))
            .map(secretary -> ResponseEntity.ok(secretary))
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/secretaries")
    public Flux<String> getSecretariesForUser(
            @RequestParam String userId) {
        return sseProxyServer.initialize()
            .thenMany(sseProxyServer.getMcpServer().getSecretariesForUser(userId));
    }

    /**
     * 获取所有用户和Secretary的对应关系
     */
    @GetMapping("/secretary-mappings")
    public Mono<Map<String, List<String>>> getAllUserSecretaryMappings() {
        return userSecretaryRegistry.getAllUserSecretaryMappings();
    }
}