package io.secretarymcp.web;

import io.secretarymcp.proxy.server.SseProxyServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/user-secretary")
public class UserSecretaryController {
    private final SseProxyServer sseProxyServer;
    
    @Autowired
    public UserSecretaryController(SseProxyServer sseProxyServer) {
        this.sseProxyServer = sseProxyServer;
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
    
    @GetMapping("/secretary")
    public Mono<ResponseEntity<String>> getSecretaryForUser(
            @RequestParam String userId) {
        return sseProxyServer.initialize()
            .then(Mono.fromCallable(() -> {
                String secretary = String.valueOf(sseProxyServer.getMcpServer().getSecretaryForUser(userId));
                if (secretary != null) {
                    return ResponseEntity.ok(secretary);
                }
                return ResponseEntity.notFound().build();
            }));
    }
}