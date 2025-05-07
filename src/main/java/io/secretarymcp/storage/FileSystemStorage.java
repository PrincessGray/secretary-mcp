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
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.ArrayList;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 基于文件系统的存储实现（弹性线程池版本）
 */
@Service
public class FileSystemStorage {
    private static final Logger log = LoggerFactory.getLogger(FileSystemStorage.class);
    
    // 弹性线程池，用于文件系统操作
    private static final Scheduler ELASTIC_SCHEDULER = Schedulers.boundedElastic();
    
    @Value("${secretary.storage.base-dir}")
    private String baseDir;
    
    /**
     * 秘书ID到名称的映射文件路径
     */
    private static final String SECRETARY_MAPPING_FILE = "secretary_map.json";
    
    /**
     * 用户ID与秘书名称的映射文件路径
     */
    private static final String USER_SECRETARY_MAPPING_FILE = "user_secretary_map.json";
    
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
                return true;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorMap(e -> {
                log.error("存储系统初始化失败: {}", e.getMessage(), e);
                return new RuntimeException("存储系统初始化失败", e);
            })
            .then(Mono.fromCallable(() -> {
                // 确保映射文件所在目录存在
                Path secretaryMappingFilePath = Path.of(baseDir, SECRETARY_MAPPING_FILE);
                if (!Files.exists(secretaryMappingFilePath)) {
                    // 如果映射文件不存在，创建一个空的映射
                    Map<String, String> emptyMap = new ConcurrentHashMap<>();
                    String json = JsonUtils.toJson(emptyMap);
                    Files.writeString(secretaryMappingFilePath, json);
                    log.info("创建秘书映射文件: {}", secretaryMappingFilePath);
                }
                return true;
            }))
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorResume(e -> {
                log.error("初始化秘书映射文件失败: {}", e.getMessage(), e);
                return Mono.just(true);
            })
            // 初始化用户-秘书映射文件
            .then(initUserSecretaryMapping())
            .then();
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
        return ensureDirectoryExists(filePath.getParent())
            .then(Mono.fromCallable(() -> {
                try {
                    String json = JsonUtils.toJson(secretary);
                    Files.writeString(filePath, json);
                    log.debug("保存秘书成功: {}", filePath);
                    return true;
                } catch (Exception e) {
                    log.error("保存秘书失败: {}", e.getMessage(), e);
                    return false;
                }
            }))
            .subscribeOn(ELASTIC_SCHEDULER)
            // 然后更新映射关系
            .flatMap(success -> {
                if (success) {
                    return saveSecretaryMapping(secretary.getId(), secretary.getName());
                }
                return Mono.just(false);
            });
    }
    
    /**
     * 加载秘书
     */
    public Mono<Secretary> loadSecretary(String secretaryId) {
        Path filePath = Constants.PathsUtil.getSecretaryFile(baseDir, secretaryId);
        return Mono.fromCallable(() -> {
                if (!Files.exists(filePath)) {
                    return null;
                }
                
                String content = Files.readString(filePath);
                Secretary secretary = JsonUtils.fromJson(content, Secretary.class);
                log.debug("加载秘书成功: {}", secretaryId);
                return secretary;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorResume(e -> {
                log.error("加载秘书失败: {}", secretaryId, e);
                return Mono.empty();
            });
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
            .subscribeOn(ELASTIC_SCHEDULER)
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
                try (var stream = Files.list(secretariesDir)) {
                    return stream
                        .filter(Files::isDirectory)
                        .collect(Collectors.toList());
                }
            })
            .subscribeOn(ELASTIC_SCHEDULER)
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
        return ensureDirectoryExists(filePath.getParent())
            .then(Mono.fromCallable(() -> {
                try {
                    String json = JsonUtils.toJson(task);
                    Files.writeString(filePath, json);
                    log.debug("保存任务成功: {}", filePath);
                    return true;
                } catch (Exception e) {
                    log.error("保存任务失败: {}", e.getMessage(), e);
                    return false;
                }
            }))
            .subscribeOn(ELASTIC_SCHEDULER);
    }
    
    /**
     * 加载任务
     */
    public Mono<RemoteTask> loadTask(String secretaryId, String taskId) {
        Path filePath = Constants.PathsUtil.getTaskFile(baseDir, secretaryId, taskId);
        return Mono.fromCallable(() -> {
                if (!Files.exists(filePath)) {
                    return null;
                }
                
                String content = Files.readString(filePath);
                RemoteTask task = JsonUtils.fromJson(content, RemoteTask.class);
                log.debug("加载任务成功: {}/{}", secretaryId, taskId);
                return task;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorResume(e -> {
                log.error("加载任务失败: {}/{}", secretaryId, taskId, e);
                return Mono.empty();
            });
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
            .subscribeOn(ELASTIC_SCHEDULER)
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
                try (var stream = Files.list(tasksDir)) {
                    return stream
                        .filter(Files::isDirectory)
                        .collect(Collectors.toList());
                }
            })
            .subscribeOn(ELASTIC_SCHEDULER)
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
        return ensureDirectoryExists(filePath.getParent())
            .then(Mono.fromCallable(() -> {
                try {
                    String json = JsonUtils.toJson(template);
                    Files.writeString(filePath, json);
                    log.debug("保存模板成功: {}", filePath);
                    return true;
                } catch (Exception e) {
                    log.error("保存模板失败: {}", e.getMessage(), e);
                    return false;
                }
            }))
            .subscribeOn(ELASTIC_SCHEDULER);
    }
    
    /**
     * 加载任务模板
     */
    public Mono<TaskTemplate> loadTemplate(String templateId) {
        Path filePath = Constants.PathsUtil.getTemplateFile(baseDir, templateId);
        return Mono.fromCallable(() -> {
                if (!Files.exists(filePath)) {
                    return null;
                }
                
                String content = Files.readString(filePath);
                TaskTemplate template = JsonUtils.fromJson(content, TaskTemplate.class);
                log.debug("加载模板成功: {}", templateId);
                return template;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorResume(e -> {
                log.error("加载模板失败: {}", templateId, e);
                return Mono.empty();
            });
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
            .subscribeOn(ELASTIC_SCHEDULER)
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
                
                // 获取所有模板目录
                try (var stream = Files.list(templatesDir)) {
                    return stream
                        .filter(Files::isDirectory)
                        .collect(Collectors.toList());
                }
            })
            .subscribeOn(ELASTIC_SCHEDULER)
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
        
        return ensureDirectoryExists(dir)
            .onErrorMap(e -> {
                log.error("创建任务子目录失败: {}", dir, e);
                return new RuntimeException("创建目录失败: " + dir, e);
            });
    }
    
    /**
     * 保存文件到任务工作目录
     */
    public Mono<Void> saveTaskFile(String secretaryId, String taskId, String fileName, String content) {
        Path filePath = Path.of(getTaskWorkDir(secretaryId, taskId), fileName);
        
        return ensureDirectoryExists(filePath.getParent())
            .then(Mono.fromCallable(() -> {
                Files.writeString(filePath, content);
                log.debug("写入文件成功: {}", filePath);
                return true;
            }))
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorMap(e -> {
                log.error("写入文件失败: {}", filePath, e);
                return new RuntimeException("写入文件失败: " + filePath, e);
            })
            .then();
    }
    
    /**
     * 从任务工作目录读取文件
     */
    public Mono<String> readTaskFile(String secretaryId, String taskId, String fileName) {
        Path filePath = Path.of(getTaskWorkDir(secretaryId, taskId), fileName);
        
        return Mono.fromCallable(() -> {
                if (!Files.exists(filePath)) {
                    return null;
                }
                
                String content = Files.readString(filePath);
                log.debug("读取文件成功: {}", filePath);
                return content;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorResume(e -> {
                log.error("读取文件失败: {}", filePath, e);
                return Mono.empty();
            });
    }
    
    /**
     * 检查任务文件是否存在
     */
    public Mono<Boolean> taskFileExists(String secretaryId, String taskId, String fileName) {
        Path filePath = Path.of(getTaskWorkDir(secretaryId, taskId), fileName);
        
        return Mono.fromCallable(() -> Files.exists(filePath))
            .subscribeOn(ELASTIC_SCHEDULER);
    }
    
    /**
     * 删除任务文件
     */
    public Mono<Void> deleteTaskFile(String secretaryId, String taskId, String fileName) {
        Path filePath = Path.of(getTaskWorkDir(secretaryId, taskId), fileName);
        
        return Mono.fromCallable(() -> {
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.debug("删除文件成功: {}", filePath);
                }
                return true;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorMap(e -> {
                log.error("删除文件失败: {}", filePath, e);
                return new RuntimeException("删除文件失败: " + filePath, e);
            })
            .then();
    }
    
    /**
     * 确保目录存在
     * 辅助方法，用于创建目录
     */
    private Mono<Void> ensureDirectoryExists(Path directory) {
        return Mono.fromCallable(() -> {
                Files.createDirectories(directory);
                return true;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorMap(e -> {
                log.error("创建目录失败: {}", directory, e);
                return new RuntimeException("创建目录失败: " + directory, e);
            })
            .then();
    }
    
    /**
     * 保存秘书ID到名称的映射关系
     * @param secretaryId 秘书ID
     * @param secretaryName 秘书名称
     * @return 操作成功与否
     */
    public Mono<Boolean> saveSecretaryMapping(String secretaryId, String secretaryName) {
        if (secretaryId == null || secretaryName == null) {
            return Mono.just(false);
        }
        
        return loadSecretaryMappings()
            .defaultIfEmpty(new ConcurrentHashMap<>())
            .flatMap(mappings -> {
                mappings.put(secretaryId, secretaryName);
                
                Path filePath = Path.of(baseDir, SECRETARY_MAPPING_FILE);
                return Mono.fromCallable(() -> {
                    try {
                        String json = JsonUtils.toJson(mappings);
                        Files.writeString(filePath, json);
                        log.debug("保存秘书映射成功: {} -> {}", secretaryId, secretaryName);
                        return true;
                    } catch (Exception e) {
                        log.error("保存秘书映射失败: {}", e.getMessage(), e);
                        return false;
                    }
                }).subscribeOn(ELASTIC_SCHEDULER);
            });
    }
    
    /**
     * 加载秘书ID到名称的映射关系
     * @return 映射关系Map
     */
    public Mono<Map<String, String>> loadSecretaryMappings() {
        Path filePath = Path.of(baseDir, SECRETARY_MAPPING_FILE);
        
        return Mono.fromCallable(() -> {
                if (!Files.exists(filePath)) {
                    return new ConcurrentHashMap<String, String>();
                }
                
                String content = Files.readString(filePath);
                Map<String, String> mappings = JsonUtils.fromJson(content, 
                        new TypeReference<ConcurrentHashMap<String, String>>() {});
                log.debug("加载秘书映射成功，共{}个映射", mappings.size());
                return mappings;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorResume(e -> {
                log.error("加载秘书映射失败: {}", e.getMessage(), e);
                return Mono.just(new ConcurrentHashMap<>());
            });
    }
    
    /**
     * 根据秘书ID获取秘书名称
     * @param secretaryId 秘书ID
     * @return 秘书名称
     */
    public Mono<String> getSecretaryNameById(String secretaryId) {
        if (secretaryId == null) {
            return Mono.empty();
        }
        
        return loadSecretaryMappings()
            .map(mappings -> mappings.get(secretaryId))
            .switchIfEmpty(
                // 如果映射中没有，尝试加载秘书信息获取名称，并保存映射
                loadSecretary(secretaryId)
                    .flatMap(secretary -> {
                        if (secretary != null) {
                            String name = secretary.getName();
                            return saveSecretaryMapping(secretaryId, name)
                                .thenReturn(name);
                        }
                        return Mono.empty();
                    })
            );
    }
    
    /**
     * 根据秘书名称获取秘书ID
     * @param secretaryName 秘书名称
     * @return 秘书ID
     */
    public Mono<String> getSecretaryIdByName(String secretaryName) {
        if (secretaryName == null) {
            return Mono.empty();
        }
        
        return loadSecretaryMappings()
            .flatMap(mappings -> {
                // 查找匹配的秘书名称
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    if (secretaryName.equals(entry.getValue())) {
                        return Mono.just(entry.getKey());
                    }
                }
                
                // 如果映射中没有，尝试从存储中查找
                return listSecretaries()
                    .filter(secretary -> secretaryName.equals(secretary.getName()))
                    .next()
                    .flatMap(secretary -> {
                        String id = secretary.getId();
                        return saveSecretaryMapping(id, secretaryName)
                            .thenReturn(id);
                    });
            });
    }
    
    /**
     * 删除秘书映射
     * @param secretaryId 秘书ID
     * @return 操作成功与否
     */
    public Mono<Boolean> deleteSecretaryMapping(String secretaryId) {
        if (secretaryId == null) {
            return Mono.just(false);
        }
        
        return loadSecretaryMappings()
            .flatMap(mappings -> {
                mappings.remove(secretaryId);
                
                Path filePath = Path.of(baseDir, SECRETARY_MAPPING_FILE);
                return Mono.fromCallable(() -> {
                    try {
                        String json = JsonUtils.toJson(mappings);
                        Files.writeString(filePath, json);
                        log.debug("删除秘书映射成功: {}", secretaryId);
                        return true;
                    } catch (Exception e) {
                        log.error("删除秘书映射失败: {}", e.getMessage(), e);
                        return false;
                    }
                }).subscribeOn(ELASTIC_SCHEDULER);
            });
    }
    
    /**
     * 加载用户ID到秘书名称列表的映射关系
     * @return 映射关系Map
     */
    public Mono<Map<String, List<String>>> loadAllUserSecretaryMappings() {
        Path filePath = Path.of(baseDir, USER_SECRETARY_MAPPING_FILE);
        
        return Mono.fromCallable(() -> {
                if (!Files.exists(filePath)) {
                    return new ConcurrentHashMap<String, List<String>>();
                }
                
                String content = Files.readString(filePath);
                try {
                    // 尝试直接解析为一对多映射
                    Map<String, List<String>> mappings = JsonUtils.fromJson(content, 
                            new TypeReference<ConcurrentHashMap<String, List<String>>>() {});
                    log.debug("加载用户-秘书多对应映射成功，共{}个映射", mappings.size());
                    return mappings;
                } catch (Exception e) {
                    // 如果失败，可能是旧格式，尝试转换
                    log.info("转换旧格式用户-秘书映射到新格式");
                    Map<String, String> oldMappings = JsonUtils.fromJson(content, 
                            new TypeReference<ConcurrentHashMap<String, String>>() {});
                    
                    Map<String, List<String>> newMappings = new ConcurrentHashMap<>();
                    for (Map.Entry<String, String> entry : oldMappings.entrySet()) {
                        List<String> secretaries = new ArrayList<>();
                        secretaries.add(entry.getValue());
                        newMappings.put(entry.getKey(), secretaries);
                    }
                    
                    // 保存新格式以便下次使用
                    try {
                        String newJson = JsonUtils.toJson(newMappings);
                        Files.writeString(filePath, newJson);
                    } catch (Exception ex) {
                        log.error("保存转换后的用户-秘书映射失败", ex);
                    }
                    
                    return newMappings;
                }
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorResume(e -> {
                log.error("加载用户-秘书映射失败: {}", e.getMessage(), e);
                return Mono.just(new ConcurrentHashMap<>());
            });
    }
    
    /**
     * 保存用户ID到秘书名称的映射关系 (支持一对多)
     * @param userId 用户ID
     * @param secretaryName 秘书名称
     * @return 操作成功与否
     */
    public Mono<Boolean> saveUserSecretaryMapping(String userId, String secretaryName) {
        if (userId == null || secretaryName == null) {
            return Mono.just(false);
        }
        
        return loadAllUserSecretaryMappings()
            .defaultIfEmpty(new ConcurrentHashMap<>())
            .flatMap(mappings -> {
                // 获取用户当前的秘书列表，如果不存在则创建
                List<String> secretaries = mappings.computeIfAbsent(userId, k -> new ArrayList<>());
                
                // 检查是否已存在，不存在才添加
                if (!secretaries.contains(secretaryName)) {
                    secretaries.add(secretaryName);
                }
                
                Path filePath = Path.of(baseDir, USER_SECRETARY_MAPPING_FILE);
                return Mono.fromCallable(() -> {
                    try {
                        String json = JsonUtils.toJson(mappings);
                        Files.writeString(filePath, json);
                        log.debug("保存用户-秘书映射成功: {} -> {}", userId, secretaryName);
                        return true;
                    } catch (Exception e) {
                        log.error("保存用户-秘书映射失败: {}", e.getMessage(), e);
                        return false;
                    }
                }).subscribeOn(ELASTIC_SCHEDULER);
            });
    }
    
    /**
     * 获取用户关联的所有秘书名称
     * @param userId 用户ID
     * @return 秘书名称列表流
     */
    public Flux<String> getSecretaryNamesByUserId(String userId) {
        if (userId == null) {
            return Flux.empty();
        }
        
        return loadAllUserSecretaryMappings()
            .flatMapMany(mappings -> {
                List<String> secretaries = mappings.get(userId);
                if (secretaries != null && !secretaries.isEmpty()) {
                    return Flux.fromIterable(secretaries);
                }
                return Flux.empty();
            });
    }
    
    /**
     * 删除用户与特定秘书的关联
     * @param userId 用户ID
     * @param secretaryName 秘书名称
     * @return 操作成功与否
     */
    public Mono<Boolean> deleteUserSecretaryMapping(String userId, String secretaryName) {
        if (userId == null || secretaryName == null) {
            return Mono.just(false);
        }
        
        return loadAllUserSecretaryMappings()
            .flatMap(mappings -> {
                List<String> secretaries = mappings.get(userId);
                if (secretaries != null) {
                    secretaries.remove(secretaryName);
                    
                    // 如果列表为空，完全删除用户
                    if (secretaries.isEmpty()) {
                        mappings.remove(userId);
                    }
                }
                
                Path filePath = Path.of(baseDir, USER_SECRETARY_MAPPING_FILE);
                return Mono.fromCallable(() -> {
                    try {
                        String json = JsonUtils.toJson(mappings);
                        Files.writeString(filePath, json);
                        log.debug("删除特定用户-秘书映射成功: {} -> {}", userId, secretaryName);
                        return true;
                    } catch (Exception e) {
                        log.error("删除特定用户-秘书映射失败: {}", e.getMessage(), e);
                        return false;
                    }
                }).subscribeOn(ELASTIC_SCHEDULER);
            });
    }
    
    /**
     * 删除用户的所有秘书关联
     * @param userId 用户ID
     * @return 操作成功与否
     */
    public Mono<Boolean> deleteAllUserSecretaryMappings(String userId) {
        if (userId == null) {
            return Mono.just(false);
        }
        
        return loadAllUserSecretaryMappings()
            .flatMap(mappings -> {
                // 直接从映射中移除用户
                mappings.remove(userId);
                
                Path filePath = Path.of(baseDir, USER_SECRETARY_MAPPING_FILE);
                return Mono.fromCallable(() -> {
                    try {
                        String json = JsonUtils.toJson(mappings);
                        Files.writeString(filePath, json);
                        log.debug("删除用户所有秘书映射成功: {}", userId);
                        return true;
                    } catch (Exception e) {
                        log.error("删除用户所有秘书映射失败: {}", e.getMessage(), e);
                        return false;
                    }
                }).subscribeOn(ELASTIC_SCHEDULER);
            });
    }
    
    /**
     * 根据用户ID获取关联的主要秘书名称
     * @param userId 用户ID
     * @return 主要秘书名称
     */
    public Mono<String> getSecretaryNameByUserId(String userId) {
        return getSecretaryNamesByUserId(userId).next();
    }
    
    /**
     * 初始化用户-秘书映射文件
     * 在initialize()方法中调用
     */
    private Mono<Boolean> initUserSecretaryMapping() {
        return Mono.fromCallable(() -> {
                // A确保映射文件所在目录存在
                Path mappingFilePath = Path.of(baseDir, USER_SECRETARY_MAPPING_FILE);
                if (!Files.exists(mappingFilePath)) {
                    // 如果映射文件不存在，创建一个空的映射(使用新格式)
                    Map<String, List<String>> emptyMap = new ConcurrentHashMap<>();
                    String json = JsonUtils.toJson(emptyMap);
                    Files.writeString(mappingFilePath, json);
                    log.info("创建用户-秘书映射文件: {}", mappingFilePath);
                }
                return true;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorResume(e -> {
                log.error("初始化用户-秘书映射文件失败: {}", e.getMessage(), e);
                return Mono.just(false);
            });
    }
}