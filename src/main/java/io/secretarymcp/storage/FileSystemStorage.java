package io.secretarymcp.storage;

import io.secretarymcp.model.RemoteTask;
import io.secretarymcp.model.Secretary;
import io.secretarymcp.model.TaskTemplate;
import io.secretarymcp.model.TemplateInfo;
import io.secretarymcp.model.SecretaryInfo;
import io.secretarymcp.util.Constants;
import io.secretarymcp.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 基于文件系统的存储实现（虚拟线程调度版本）
 */
@Service
public class FileSystemStorage {
    private static final Logger log = LoggerFactory.getLogger(FileSystemStorage.class);
    
    // 创建虚拟线程调度器
    private static final Scheduler VIRTUAL_THREADS = Schedulers.fromExecutor(
        Executors.newVirtualThreadPerTaskExecutor()
    );
    
    @Value("${secretary.storage.base-dir}")
    private String baseDir;
    
    /**
     * 初始化存储目录
     */
    public Mono<Void> initialize() {
        return Mono.fromCallable(() -> {
            // 创建基础目录
            Files.createDirectories(Path.of(baseDir));
            
            // 创建秘书目录
            Files.createDirectories(Constants.PathsUtil.getSecretaryDir(baseDir));
            
            // 创建模板目录
            Files.createDirectories(Constants.PathsUtil.getTemplatesDir(baseDir));
            
            log.info("存储系统初始化完成，基础目录: {}", baseDir);
            return null;
        })
        .subscribeOn(VIRTUAL_THREADS) // 使用虚拟线程调度器
        .onErrorMap(e -> {
            log.error("存储系统初始化失败: {}", e.getMessage(), e);
            return new RuntimeException("存储系统初始化失败", e);
        }).then();
    }
    
    // ====== 秘书相关操作 ======
    
    /**
     * 保存秘书
     */
    public Mono<Boolean> saveSecretary(Secretary secretary) {
        if (secretary == null) {
            return Mono.just(false);
        }
        
        Path filePath = Constants.PathsUtil.getSecretaryFile(baseDir, secretary.getId());
        return JsonUtils.saveToFileAsync(secretary, filePath, VIRTUAL_THREADS) // 传入虚拟线程调度器
                .thenReturn(true)
                .onErrorResume(e -> {
                    log.error("保存秘书失败: {}", e.getMessage(), e);
                    return Mono.just(false);
                });
    }
    
    /**
     * 加载秘书
     */
    public Mono<Secretary> loadSecretary(String secretaryId) {
        Path filePath = Constants.PathsUtil.getSecretaryFile(baseDir, secretaryId);
        return JsonUtils.loadFromFileAsync(filePath, Secretary.class, VIRTUAL_THREADS); // 传入虚拟线程调度器
    }
    
    /**
     * 删除秘书
     */
    public Mono<Boolean> deleteSecretary(String secretaryId) {
        Path secretaryDir = Path.of(baseDir, "secretaries", secretaryId);
        
        return Mono.fromCallable(() -> {
            if (!Files.exists(secretaryDir)) {
                return false;
            }
            
            // 递归删除目录
            Files.walkFileTree(secretaryDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            
            log.info("已删除秘书: {}", secretaryId);
            return true;
        })
        .subscribeOn(VIRTUAL_THREADS) // 使用虚拟线程调度器
        .onErrorResume(e -> {
            log.error("删除秘书失败: {}", secretaryId, e);
            return Mono.just(false);
        });
    }
    
    /**
     * 列出所有秘书
     */
    public Flux<Secretary> listSecretaries() {
        Path secretariesDir = Constants.PathsUtil.getSecretaryDir(baseDir);
        
        return Mono.fromCallable(() -> {
            if (!Files.exists(secretariesDir)) {
                return List.<Path>of();
            }
            
            // 获取所有秘书目录
            try (var stream = Files.list(secretariesDir)){
                return stream
                    .filter(Files::isDirectory)
                    .collect(Collectors.toList());
            }
        })
        .subscribeOn(VIRTUAL_THREADS) // 使用虚拟线程调度器
        .flatMapMany(Flux::fromIterable)
        .flatMap(dir -> {
            String secretaryId = dir.getFileName().toString();
            return loadSecretary(secretaryId);
        })
        .filter(Objects::nonNull);
    }

    
    // ====== 任务相关操作 ======
    
    /**
     * 保存任务
     */
    public Mono<Boolean> saveTask(String secretaryId, RemoteTask task) {
        if (task == null) {
            return Mono.just(false);
        }
        
        Path filePath = Constants.PathsUtil.getTaskFile(baseDir, secretaryId, task.getId());
        return JsonUtils.saveToFileAsync(task, filePath, VIRTUAL_THREADS) // 传入虚拟线程调度器
                .thenReturn(true)
                .onErrorResume(e -> {
                    log.error("保存任务失败: {}", e.getMessage(), e);
                    return Mono.just(false);
                });
    }
    
    /**
     * 加载任务
     */
    public Mono<RemoteTask> loadTask(String secretaryId, String taskId) {
        Path filePath = Constants.PathsUtil.getTaskFile(baseDir, secretaryId, taskId);
        return JsonUtils.loadFromFileAsync(filePath, RemoteTask.class, VIRTUAL_THREADS); // 传入虚拟线程调度器
    }
    
    /**
     * 删除任务
     */
    public Mono<Boolean> deleteTask(String secretaryId, String taskId) {
        Path taskDir = Path.of(baseDir, "secretaries", secretaryId, "tasks", taskId);
        
        return Mono.fromCallable(() -> {
            if (!Files.exists(taskDir)) {
                return false;
            }
            
            // 递归删除目录
            Files.walkFileTree(taskDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            
            log.info("已删除任务: {}/{}", secretaryId, taskId);
            return true;
        })
        .subscribeOn(VIRTUAL_THREADS) // 使用虚拟线程调度器
        .onErrorResume(e -> {
            log.error("删除任务失败: {}/{}", secretaryId, taskId, e);
            return Mono.just(false);
        });
    }
    
    /**
     * 列出秘书的所有任务
     */
    public Flux<RemoteTask> listTasks(String secretaryId) {
        Path tasksDir = Constants.PathsUtil.getTasksDir(baseDir, secretaryId);
        
        return Mono.fromCallable(() -> {
            if (!Files.exists(tasksDir)) {
                return List.<Path>of();
            }
            
            // 获取所有任务目录
            try (var stream = Files.list(tasksDir)){
                return stream
                    .filter(Files::isDirectory)
                    .collect(Collectors.toList());
            }
        })
        .subscribeOn(VIRTUAL_THREADS) // 使用虚拟线程调度器
        .flatMapMany(Flux::fromIterable)
        .flatMap(dir -> {
            String taskId = dir.getFileName().toString();
            return loadTask(secretaryId, taskId);
        })
        .filter(Objects::nonNull);
    }
    
    // ====== 模板相关操作 ======
    
    /**
     * 保存任务模板
     */
    public Mono<Boolean> saveTemplate(TaskTemplate template) {
        if (template == null) {
            return Mono.just(false);
        }
        
        Path filePath = Constants.PathsUtil.getTemplateFile(baseDir, template.getId());
        return Mono.fromCallable(() -> {
            // 确保目录存在
            Files.createDirectories(filePath.getParent());
            return filePath;
        })
        .subscribeOn(VIRTUAL_THREADS) // 使用虚拟线程调度器
        .flatMap(path -> JsonUtils.saveToFileAsync(template, path, VIRTUAL_THREADS))
        .thenReturn(true)
        .onErrorResume(e -> {
            log.error("保存模板失败: {}", e.getMessage(), e);
            return Mono.just(false);
        });
    }
    
    /**
     * 加载任务模板
     */
    public Mono<TaskTemplate> loadTemplate(String templateId) {
        Path filePath = Constants.PathsUtil.getTemplateFile(baseDir, templateId);
        return JsonUtils.loadFromFileAsync(filePath, TaskTemplate.class, VIRTUAL_THREADS); // 传入虚拟线程调度器
    }
    
    /**
     * 删除任务模板
     */
    public Mono<Boolean> deleteTemplate(String templateId) {
        Path templateDir = Path.of(baseDir, "templates", templateId);
        
        return Mono.fromCallable(() -> {
            if (!Files.exists(templateDir)) {
                return false;
            }
            
            // 递归删除目录
            Files.walkFileTree(templateDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            
            log.info("已删除模板: {}", templateId);
            return true;
        })
        .subscribeOn(VIRTUAL_THREADS) // 使用虚拟线程调度器
        .onErrorResume(e -> {
            log.error("删除模板失败: {}", templateId, e);
            return Mono.just(false);
        });
    }

    /**
     * 列出所有任务模板
     */
    public Flux<TaskTemplate> listTemplates() {
        Path templatesDir = Constants.PathsUtil.getTemplatesDir(baseDir);

        return Mono.fromCallable(() -> {
                    if (!Files.exists(templatesDir)) {
                        return List.<Path>of();
                    }

                    // 使用try-with-resources确保流被关闭
                    try (var stream = Files.list(templatesDir)) {
                        return stream
                                .filter(Files::isDirectory)
                                .collect(Collectors.toList());
                    }
                })
                .subscribeOn(VIRTUAL_THREADS) // 使用虚拟线程调度器
                .flatMapMany(Flux::fromIterable)
                .flatMap(dir -> {
                    String templateId = dir.getFileName().toString();
                    return loadTemplate(templateId);
                })
                .filter(Objects::nonNull);
    }
    
    /**
     * 列出所有秘书的基本信息
     */
    public Flux<SecretaryInfo> listSecretaryInfos() {
        return listSecretaries()
                .map(SecretaryInfo::fromSecretary);
    }

    /**
     * 列出所有任务模板的基本信息
     */
    public Flux<TemplateInfo> listTemplateInfos() {
        return listTemplates()
                .map(TemplateInfo::fromTemplate);
    }
    
    /**
     * 获取任务工作目录
     */
    public String getTaskWorkDir(String secretaryId, String taskId) {
        return Constants.PathsUtil.getTaskWorkDir(baseDir, secretaryId, taskId).toString();
    }
    
    /**
     * 创建任务工作目录下的子目录
     */
    public Mono<Void> createTaskSubDirectory(String secretaryId, String taskId, String subDirName) {
        Path dir = Path.of(getTaskWorkDir(secretaryId, taskId), subDirName);
        
        return Mono.fromCallable(() -> {
            Files.createDirectories(dir);
            return null;
        })
        .subscribeOn(VIRTUAL_THREADS) // 使用虚拟线程调度器
        .onErrorMap(e -> {
            log.error("创建任务子目录失败: {}", dir, e);
            return new RuntimeException("创建目录失败: " + dir, e);
        }).then();
    }
    
    /**
     * 保存文件到任务工作目录
     */
    public Mono<Void> saveTaskFile(String secretaryId, String taskId, String fileName, String content) {
        Path filePath = Path.of(getTaskWorkDir(secretaryId, taskId), fileName);
        
        return JsonUtils.writeFileAsync(content, filePath, VIRTUAL_THREADS); // 传入虚拟线程调度器
    }
    
    /**
     * 从任务工作目录读取文件
     */
    public Mono<String> readTaskFile(String secretaryId, String taskId, String fileName) {
        Path filePath = Path.of(getTaskWorkDir(secretaryId, taskId), fileName);
        
        return JsonUtils.readFileAsStringAsync(filePath, VIRTUAL_THREADS); // 传入虚拟线程调度器
    }
    
        /**
     * 检查任务文件是否存在
     */
    public Mono<Boolean> taskFileExists(String secretaryId, String taskId, String fileName) {
        Path filePath = Path.of(getTaskWorkDir(secretaryId, taskId), fileName);
        
        return JsonUtils.existsAsync(filePath, VIRTUAL_THREADS); // 传入虚拟线程调度器
    }
    
    /**
     * 删除任务文件
     */
    public Mono<Void> deleteTaskFile(String secretaryId, String taskId, String fileName) {
        Path filePath = Path.of(getTaskWorkDir(secretaryId, taskId), fileName);
        
        return JsonUtils.deleteFileAsync(filePath, VIRTUAL_THREADS); // 传入虚拟线程调度器
    }
}