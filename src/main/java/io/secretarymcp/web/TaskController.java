package io.secretarymcp.web;

import io.secretarymcp.model.RemoteTask;
import io.secretarymcp.service.TaskService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 任务控制器 - 提供任务相关的API接口
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {
    private static final Logger log = LoggerFactory.getLogger(TaskController.class);
    
    private final TaskService taskService;
    
    /**
     * 获取指定任务的详细信息
     */
    @GetMapping("/{taskId}")
    public Mono<RemoteTask> getTask(
            @PathVariable String taskId,
            @RequestParam String secretaryId) {
        
        return taskService.getTask(secretaryId, taskId)
                .onErrorResume(e -> {
                    log.error("获取任务失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "任务不存在"));
                });
    }
    
    /**
     * 创建新任务
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RemoteTask> createTask(@RequestBody CreateTaskRequest request) {
        return taskService.createTask(
                request.getSecretaryId(), 
                request.getTemplateId(), 
                request.getName())
                .onErrorResume(e -> {
                    log.error("创建任务失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "创建任务失败: " + e.getMessage()));
                });
    }
    
    /**
     * 删除任务
     */
    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTask(
            @PathVariable String taskId,
            @RequestParam String secretaryId) {
        
        return taskService.deleteTask(secretaryId, taskId)
                .onErrorResume(e -> {
                    log.error("删除任务失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "删除任务失败: " + e.getMessage()));
                });
    }
    
    /**
     * 激活任务
     */
    @PostMapping("/{taskId}/activate")
    public Mono<RemoteTask> activateTask(
            @PathVariable String taskId,
            @RequestParam String secretaryId) {
        
        return taskService.activateTask(secretaryId, taskId)
                .onErrorResume(e -> {
                    log.error("激活任务失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "激活任务失败: " + e.getMessage()));
                });
    }
    
    /**
     * 停用任务
     */
    @PostMapping("/{taskId}/deactivate")
    public Mono<RemoteTask> deactivateTask(
            @PathVariable String taskId,
            @RequestParam String secretaryId) {
        
        return taskService.deactivateTask(secretaryId, taskId)
                .onErrorResume(e -> {
                    log.error("停用任务失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "停用任务失败: " + e.getMessage()));
                });
    }
    
    /**
     * 更新任务配置
     */
    @PutMapping("/{taskId}/config")
    public Mono<RemoteTask> updateTaskConfig(
            @PathVariable String taskId,
            @RequestParam String secretaryId,
            @RequestBody Map<String, Object> configUpdates) {
        
        return taskService.updateTaskConfig(secretaryId, taskId, configUpdates)
                .onErrorResume(e -> {
                    log.error("更新任务配置失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "更新任务配置失败: " + e.getMessage()));
                });
    }
    
    /**
     * 应用自定义连接参数
     */
    @PutMapping("/{taskId}/connection")
    public Mono<RemoteTask> applyCustomConnectionParams(
            @PathVariable String taskId,
            @RequestParam String secretaryId,
            @RequestBody Map<String, Object> customParams) {
        
        return taskService.applyCustomConnectionParams(secretaryId, taskId, customParams)
                .onErrorResume(e -> {
                    log.error("应用自定义连接参数失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "应用自定义连接参数失败: " + e.getMessage()));
                });
    }
    

    /**
     * 创建任务的请求对象
     */
    @Setter
    @Getter
    public static class CreateTaskRequest {
        private String secretaryId;
        private String templateId;
        private String name;

    }
}