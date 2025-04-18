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
        
        // 检查名称是否已被使用
        return storage.listSecretaryInfos()
                .filter(info -> name.equals(info.getName()))
                .hasElements()
                .flatMap(nameExists -> {
                    if (nameExists) {
                        return Mono.error(new IllegalArgumentException("秘书名称已存在: " + name));
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
        log.info("正在尝试激活秘书: {}", secretaryId);
        
        return getSecretary(secretaryId)
                .flatMap(secretary -> {
                    // 检查是否已激活
                    if (secretary.isActive()) {
                        log.info("秘书已处于激活状态，无需再次激活: {}", secretaryId);
                        return Mono.just(secretary);
                    }
                    
                    log.debug("开始执行秘书激活流程: {}", secretaryId);
                    
                    // 1. 先停用所有其他秘书
                    return deactivateAllOtherSecretaries(secretaryId)
                            .doOnSuccess(v -> log.debug("已停用其他所有秘书"))
                            .then(Mono.defer(() -> {
                                // 2. 设置当前秘书为激活状态
                                secretary.activate();
                                log.debug("已将秘书状态设为激活: {}", secretaryId);
                                
                                // 3. 保存秘书状态
                                return storage.saveSecretary(secretary)
                                        .doOnNext(success -> {
                                            if (success) {
                                                log.debug("已保存秘书激活状态: {}", secretaryId);
                                            } else {
                                                log.warn("保存秘书激活状态失败: {}", secretaryId);
                                            }
                                        })
                                        // 4. 激活该秘书的所有任务
                                        .then(taskService.activateAllTasks(secretaryId))
                                        .doOnSuccess(v -> log.debug("已激活秘书的所有任务: {}", secretaryId))
                                        .thenReturn(secretary);
                            }))
                            .doOnSuccess(s -> log.info("秘书激活成功: {}", secretaryId))
                            .doOnError(e -> log.error("秘书激活失败: {} (原因: {})", secretaryId, e.getMessage()));
                });
    }
    
    /**
     * 停用除指定秘书外的所有秘书
     */
    private Mono<Void> deactivateAllOtherSecretaries(String exceptSecretaryId) {
        log.debug("正在停用除 {} 外的所有其他秘书", exceptSecretaryId);
        
        return listSecretaries()
                .filter(secretaryInfo -> secretaryInfo.isActive() && !secretaryInfo.getId().equals(exceptSecretaryId))
                .doOnNext(secretaryInfo -> log.debug("准备停用秘书: {}", secretaryInfo.getId()))
                .flatMap(secretaryInfo -> deactivateSecretary(secretaryInfo.getId()))
                .then()
                .doOnSuccess(v -> log.debug("已完成所有其他秘书的停用操作"));
    }
    
    /**
     * 停用秘书
     */
    public Mono<Secretary> deactivateSecretary(String secretaryId) {
        log.info("正在尝试停用秘书: {}", secretaryId);
        
        return getSecretary(secretaryId)
                .flatMap(secretary -> {
                    // 检查是否已停用
                    if (!secretary.isActive()) {
                        log.info("秘书已处于停用状态，无需再次停用: {}", secretaryId);
                        return Mono.just(secretary);
                    }
                    
                    log.debug("开始执行秘书停用流程: {}", secretaryId);
                    
                    // 停用所有任务
                    return taskService.deactivateAllTasks(secretaryId)
                            .doOnSuccess(v -> log.debug("已停用秘书的所有任务: {}", secretaryId))
                            .then(Mono.defer(() -> {
                                // 设置停用状态
                                secretary.deactivate();
                                log.debug("已将秘书状态设为停用: {}", secretaryId);
                                
                                // 保存更新
                                return storage.saveSecretary(secretary)
                                        .doOnNext(success -> {
                                            if (success) {
                                                log.debug("已保存秘书停用状态: {}", secretaryId);
                                            } else {
                                                log.warn("保存秘书停用状态失败: {}", secretaryId);
                                            }
                                        })
                                        .thenReturn(secretary);
                            }))
                            .doOnSuccess(s -> log.info("秘书停用成功: {}", secretaryId))
                            .doOnError(e -> log.error("秘书停用失败: {} (原因: {})", secretaryId, e.getMessage()));
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