package io.secretarymcp.service;

import io.secretarymcp.model.ConnectionProfile;
import io.secretarymcp.model.RemoteTask;
import io.secretarymcp.model.TaskTemplate;
import io.secretarymcp.model.TemplateInfo;
import io.secretarymcp.proxy.ProxyServer;
import io.secretarymcp.proxy.client.UpstreamClientConfig;
import io.secretarymcp.storage.FileSystemStorage;
import io.secretarymcp.util.Constants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 任务服务 - 负责任务管理
 */
@Service
@RequiredArgsConstructor
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    
    private final FileSystemStorage storage;
    private final ProxyServer proxyServer; // 使用ProxyServer接口替代原先的McpClientAdapter
    
    /**
     * 创建任务模板
     */
    public Mono<TaskTemplate> createTemplate(TaskTemplate template) {
        if (template == null) {
            return Mono.error(new IllegalArgumentException("模板不能为空"));
        }
        
        log.info("创建任务模板: {}", template.getName());
        
        return storage.saveTemplate(template)
                .flatMap(success -> {
                    if (success) {
                        log.info("模板创建成功: {}", template.getId());
                        return Mono.just(template);
                    } else {
                        return Mono.error(new RuntimeException("保存模板失败"));
                    }
                });
    }
    
    /**
     * 获取模板详情
     */
    public Mono<TaskTemplate> getTemplate(String templateId) {
        return storage.loadTemplate(templateId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("模板不存在: " + templateId)));
    }
    
    /**
     * 列出所有模板信息
     */
    public Flux<TemplateInfo> listTemplates() {
        return storage.listTemplateInfos();
    }
    
    /**
     * 删除模板
     */
    public Mono<Void> deleteTemplate(String templateId) {
        return storage.deleteTemplate(templateId)
                .flatMap(success -> {
                    if (success) {
                        log.info("删除模板成功: {}", templateId);
                        return Mono.empty();
                    } else {
                        return Mono.error(new RuntimeException("删除模板失败: " + templateId));
                    }
                });
    }
    
    /**
     * 为秘书创建任务
     */
    public Mono<RemoteTask> createTask(String secretaryId, String templateId, String name) {
        // 检查秘书是否存在
        return storage.loadSecretary(secretaryId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("秘书不存在: " + secretaryId)))
                .flatMap(secretary -> {
                    // 检查任务名称是否已存在于此秘书下
                    return storage.listTasks(secretaryId)
                            .filter(task -> name.equals(task.getName()))
                            .hasElements()
                            .flatMap(nameExists -> {
                                if (nameExists) {
                                    return Mono.error(new IllegalArgumentException(
                                            "任务名称在此秘书下已存在: " + name));
                                }
                                
                                // 加载模板
                                return getTemplate(templateId)
                                        .flatMap(template -> {
                                            // 创建任务
                                            RemoteTask task = RemoteTask.fromTemplate(template, secretaryId,secretary.getName(), name);
                                            
                                            // 将任务添加到秘书
                                            secretary.addTask(task.getId());
                                            
                                            // 保存任务和更新秘书
                                            return storage.saveTask(secretaryId, task)
                                                    .then(storage.saveSecretary(secretary))
                                                    .thenReturn(task);
                                        });
                            });
                });
    }
    
    /**
     * 获取任务详情
     */
    public Mono<RemoteTask> getTask(String secretaryId, String taskId) {
        return storage.loadTask(secretaryId, taskId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "任务不存在: " + secretaryId + "/" + taskId)));
    }
    
    /**
     * 获取秘书的所有任务
     */
    public Flux<RemoteTask> listTasks(String secretaryId) {
        return storage.listTasks(secretaryId);
    }
    
    /**
     * 激活任务
     */
    public Mono<RemoteTask> activateTask(String secretaryId, String taskId) {
        return getTask(secretaryId, taskId)
                .flatMap(task -> {
                    // 如果任务已经激活，直接返回
                    if (task.getStatus() == Constants.TaskStatus.ACTIVE) {
                        return Mono.just(task);
                    }
                    
                    log.info("激活任务: {}/{}", secretaryId, taskId);
                    
                    // 使用静态工厂方法
                    UpstreamClientConfig config = UpstreamClientConfig.fromRemoteTask(task);
                    
                    // 使用代理服务器添加上游客户端
                    return proxyServer.addUpstreamClient(config)
                            .then(Mono.defer(() -> {
                                // 更新任务状态
                                task.activate();
                                
                                // 保存更新
                                return storage.saveTask(secretaryId, task)
                                        .thenReturn(task);
                            }))
                            .onErrorResume(e -> {
                                // 添加失败，设置错误状态
                                log.error("任务激活失败: {}", e.getMessage(), e);
                                task.setError("激活失败: " + e.getMessage());
                                return storage.saveTask(secretaryId, task)
                                        .then(Mono.error(new RuntimeException("任务激活失败", e)));
                            });
                });
    }
    
    /**
     * 停用任务
     */
    public Mono<RemoteTask> deactivateTask(String secretaryId, String taskId) {
        // 获取任务
        return getTask(secretaryId, taskId)
                .flatMap(task -> {
                    // 如果任务已经停用，直接返回
                    if (task.getStatus() != Constants.TaskStatus.ACTIVE) {
                        return Mono.just(task);
                    }
                    
                    log.info("停用任务: {}/{}", secretaryId, taskId);
                    
                    // 使用代理服务器移除上游客户端
                    return proxyServer.removeUpstreamClient(task.getSecretaryName(),taskId)
                            .then(Mono.defer(() -> {
                                // 更新任务状态
                                task.deactivate();
                                
                                // 保存更新
                                return storage.saveTask(secretaryId, task)
                                        .thenReturn(task);
                            }))
                            .onErrorResume(e -> {
                                log.error("停用任务过程中发生错误: {}", e.getMessage(), e);
                                
                                // 更新任务状态为错误
                                task.setError("停用失败: " + e.getMessage());
                                
                                // 保存更新
                                return storage.saveTask(secretaryId, task)
                                        .then(Mono.error(new RuntimeException("停用任务失败", e)));
                            });
                });
    }
    
    /**
     * 停用秘书的所有任务
     */
    public Mono<Void> deactivateAllTasks(String secretaryId) {
        log.info("停用秘书的所有任务: {}", secretaryId);
        
        return listTasks(secretaryId)
                .filter(task -> task.getStatus() == Constants.TaskStatus.ACTIVE)
                .flatMap(task -> deactivateTask(secretaryId, task.getId()))
                .then();
    }
    
    /**
     * 删除任务
     */
    public Mono<Void> deleteTask(String secretaryId, String taskId) {
        // 先获取秘书
        return storage.loadSecretary(secretaryId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(STR."秘书不存在: \{secretaryId}")))
                .flatMap(secretary -> {
                    // 获取任务
                    return getTask(secretaryId, taskId)
                            .flatMap(task -> {
                                // 如果任务处于激活状态，先停用
                                if (task.getStatus() == Constants.TaskStatus.ACTIVE) {
                                    return deactivateTask(secretaryId, taskId)
                                            .then();
                                }
                                return Mono.empty();
                            })
                            .then(Mono.defer(() -> {
                                // 从秘书中移除任务
                                secretary.removeTask(taskId);
                                
                                // 删除任务
                                return storage.deleteTask(secretaryId, taskId)
                                        .then(storage.saveSecretary(secretary))
                                        .then();
                            }));
                });
    }
    
    /**
     * 应用自定义连接参数
     */
    public Mono<RemoteTask> applyCustomParams(
            String secretaryId, 
            String taskId, 
            Map<String, Object> customParams) {
        
        return getTask(secretaryId, taskId)
                .flatMap(task -> {
                    // 任务已激活则不能修改参数
                    if (task.getStatus() == Constants.TaskStatus.ACTIVE) {
                        return Mono.error(new IllegalStateException("不能修改激活状态的任务参数"));
                    }
                    
                    // 应用自定义参数（使用新的applyCustomParams方法）
                    task.applyCustomParams(customParams);
                    
                    // 保存任务
                    return storage.saveTask(secretaryId, task)
                            .thenReturn(task);
                });
    }
    
    /**
     * 更新任务配置
     * 注意：此方法仅更新通用配置，不会修改连接特定配置
     */
    public Mono<RemoteTask> updateTaskConfig(String secretaryId, String taskId, Map<String, Object> configUpdates) {
        return getTask(secretaryId, taskId)
                .flatMap(task -> {
                    // 更新配置
                    task.updateConfig(configUpdates);
                    
                    // 保存任务
                    return storage.saveTask(secretaryId, task)
                            .thenReturn(task);
                });
    }
    
    /**
     * 更新任务连接配置
     * 此方法允许直接更新连接配置的特定部分
     */
    public Mono<RemoteTask> updateConnectionConfig(
            String secretaryId,
            String taskId,
            ConnectionProfile connectionProfile) {
        
        return getTask(secretaryId, taskId)
                .flatMap(task -> {
                    // 任务已激活则不能修改连接配置
                    if (task.getStatus() == Constants.TaskStatus.ACTIVE) {
                        return Mono.error(new IllegalStateException("不能修改激活状态的任务连接配置"));
                    }
                    
                    // 设置新的连接配置
                    task.setConnectionProfile(connectionProfile);
                    
                    // 保存任务
                    return storage.saveTask(secretaryId, task)
                            .thenReturn(task);
                });
    }
    
    /**
     * 激活秘书的所有任务，使用串行方式避免冲突
     */
    public Mono<Void> activateAllTasks(String secretaryId) {
        log.info("激活秘书的所有任务: {}", secretaryId);
        
        return storage.listTasks(secretaryId)
                // 使用concatMap保证串行执行，避免并发冲突
                .concatMap(task -> {
                    log.info("依次激活任务: {}/{}", secretaryId, task.getId());
                    // 注意参数顺序：taskId, secretaryId
                    return activateTask(secretaryId, task.getId())
                        .onErrorResume(e -> {
                            log.error("任务激活失败，但将继续激活其他任务: {}/{} (错误: {})", 
                                    secretaryId, task.getId(), e.getMessage());
                            return Mono.empty(); // 继续下一个任务
                        });
                })
                .then()
                .doOnSuccess(v -> log.info("所有任务激活完成: {}", secretaryId));
    }
}