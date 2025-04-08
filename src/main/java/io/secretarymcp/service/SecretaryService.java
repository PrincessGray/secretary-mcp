package io.secretarymcp.service;

import io.secretarymcp.model.Secretary;
import io.secretarymcp.model.SecretaryInfo;
import io.secretarymcp.model.RemoteTask;
import io.secretarymcp.storage.FileSystemStorage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 秘书服务 - 负责秘书管理
 */
@Service
@RequiredArgsConstructor
public class SecretaryService {
    private static final Logger log = LoggerFactory.getLogger(SecretaryService.class);
    
    private final FileSystemStorage storage;
    private final TaskService taskService;
    
    /**
     * 创建新秘书
     */
    public Mono<Secretary> createSecretary(String name, String description) {
        if (name == null || name.isBlank()) {
            return Mono.error(new IllegalArgumentException("秘书名称不能为空"));
        }
        
        log.info("创建新秘书: {}", name);
        
        Secretary secretary = Secretary.create(name, description);
        return storage.saveSecretary(secretary)
                .flatMap(success -> {
                    if (success) {
                        log.info("秘书创建成功: {}", secretary.getId());
                        return Mono.just(secretary);
                    } else {
                        return Mono.error(new RuntimeException("保存秘书失败"));
                    }
                });
    }
    
    /**
     * 获取秘书详情
     */
    public Mono<Secretary> getSecretary(String secretaryId) {
        return storage.loadSecretary(secretaryId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("秘书不存在: " + secretaryId)));
    }
    
    /**
     * 列出所有秘书摘要信息
     */
    public Flux<SecretaryInfo> listSecretaries() {
        return storage.listSecretaryInfos();
    }
    
    /**
     * 激活秘书
     */
    public Mono<Secretary> activateSecretary(String secretaryId) {
        return getSecretary(secretaryId)
                .flatMap(secretary -> {
                    // 检查是否已激活
                    if (secretary.isActive()) {
                        return Mono.just(secretary);
                    }
                    
                    // 设置激活状态
                    secretary.activate();
                    
                    // 保存更新
                    return storage.saveSecretary(secretary)
                            .thenReturn(secretary);
                });
    }
    
    /**
     * 停用秘书
     */
    public Mono<Secretary> deactivateSecretary(String secretaryId) {
        return getSecretary(secretaryId)
                .flatMap(secretary -> {
                    // 检查是否已停用
                    if (!secretary.isActive()) {
                        return Mono.just(secretary);
                    }
                    
                    // 停用所有任务
                    return taskService.deactivateAllTasks(secretaryId)
                            .then(Mono.defer(() -> {
                                // 设置停用状态
                                secretary.deactivate();
                                
                                // 保存更新
                                return storage.saveSecretary(secretary)
                                        .thenReturn(secretary);
                            }));
                });
    }
    
    /**
     * 删除秘书
     */
    public Mono<Void> deleteSecretary(String secretaryId) {
        return getSecretary(secretaryId)
                .flatMap(secretary -> {
                    // 如果秘书处于激活状态，先停用
                    if (secretary.isActive()) {
                        return deactivateSecretary(secretaryId)
                                .then();
                    }
                    return Mono.empty();
                })
                // 执行删除
                .then(storage.deleteSecretary(secretaryId))
                .flatMap(success -> {
                    if (success) {
                        log.info("删除秘书成功: {}", secretaryId);
                        return Mono.empty();
                    } else {
                        return Mono.error(new RuntimeException("删除秘书失败: " + secretaryId));
                    }
                });
    }
    
    /**
     * 获取秘书的所有任务
     */
    public Flux<RemoteTask> getSecretaryTasks(String secretaryId) {
        return getSecretary(secretaryId)
                .flatMapMany(secretary -> storage.listTasks(secretaryId));
    }
}