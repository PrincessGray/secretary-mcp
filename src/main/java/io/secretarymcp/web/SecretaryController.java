package io.secretarymcp.web;

import io.secretarymcp.model.Secretary;
import io.secretarymcp.model.SecretaryInfo;
import io.secretarymcp.model.RemoteTask;
import io.secretarymcp.service.SecretaryService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 秘书控制器 - 提供秘书相关的API接口
 */
@RestController
@RequestMapping("/api/secretaries")
@RequiredArgsConstructor
public class SecretaryController {
    private static final Logger log = LoggerFactory.getLogger(SecretaryController.class);
    
    private final SecretaryService secretaryService;
    
    /**
     * 获取所有秘书的基本信息
     */
    @GetMapping
    public Flux<SecretaryInfo> getAllSecretaries() {
        return secretaryService.listSecretaries();
    }
    
    /**
     * 获取指定秘书的详细信息
     */
    @GetMapping("/{id}")
    public Mono<Secretary> getSecretary(@PathVariable String id) {
        return secretaryService.getSecretary(id)
                .onErrorResume(e -> {
                    log.error("获取秘书失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "秘书不存在: " + id));
                });
    }
    
    /**
     * 创建新秘书
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Secretary> createSecretary(@RequestBody CreateSecretaryRequest request) {
        return secretaryService.createSecretary(request.getName(), request.getDescription())
                .onErrorResume(e -> {
                    log.error("创建秘书失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "创建秘书失败: " + e.getMessage()));
                });
    }
    
    /**
     * 删除秘书
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteSecretary(@PathVariable String id) {
        return secretaryService.deleteSecretary(id)
                .onErrorResume(e -> {
                    log.error("删除秘书失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "删除秘书失败: " + e.getMessage()));
                });
    }
    
    /**
     * 激活秘书
     */
    @PostMapping("/{id}/activate")
    public Mono<Secretary> activateSecretary(@PathVariable String id) {
        return secretaryService.activateSecretary(id)
                .onErrorResume(e -> {
                    log.error("激活秘书失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "激活秘书失败: " + e.getMessage()));
                });
    }
    
    /**
     * 停用秘书
     */
    @PostMapping("/{id}/deactivate")
    public Mono<Secretary> deactivateSecretary(@PathVariable String id) {
        return secretaryService.deactivateSecretary(id)
                .onErrorResume(e -> {
                    log.error("停用秘书失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "停用秘书失败: " + e.getMessage()));
                });
    }
    
    /**
     * 获取秘书的所有任务
     */
    @GetMapping("/{id}/tasks")
    public Flux<RemoteTask> getSecretaryTasks(@PathVariable String id) {
        return secretaryService.getSecretaryTasks(id)
                .onErrorResume(e -> {
                    log.error("获取秘书任务失败: {}", e.getMessage());
                    return Flux.error(new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "获取秘书任务失败: " + e.getMessage()));
                });
    }
    
    /**
     * 创建秘书的请求对象
     */
    @Setter
    @Getter
    public static class CreateSecretaryRequest {
        private String name;
        private String description;

    }
}