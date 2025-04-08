package io.secretarymcp.web;

import io.secretarymcp.model.TaskTemplate;
import io.secretarymcp.model.TemplateInfo;
import io.secretarymcp.service.TaskService;
import io.secretarymcp.util.Constants.TaskType;
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

import java.util.Map;

/**
 * 模板控制器 - 提供任务模板相关的API接口
 */
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {
    private static final Logger log = LoggerFactory.getLogger(TemplateController.class);
    
    private final TaskService taskService;
    
    /**
     * 获取所有模板的基本信息
     */
    @GetMapping
    public Flux<TemplateInfo> getAllTemplates() {
        return taskService.listTemplates();
    }
    
    /**
     * 获取指定模板的详细信息
     */
    @GetMapping("/{id}")
    public Mono<TaskTemplate> getTemplate(@PathVariable String id) {
        return taskService.getTemplate(id)
                .onErrorResume(e -> {
                    log.error("获取模板失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "模板不存在: " + id));
                });
    }
    
    /**
     * 创建新模板
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TaskTemplate> createTemplate(@RequestBody CreateTemplateRequest request) {
        TaskType connectionType;
        try {
            connectionType = TaskType.fromValue(request.getConnectionType());
        } catch (Exception e) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "无效的连接类型: " + request.getConnectionType()));
        }
        
        // 创建模板基础对象
        TaskTemplate template = TaskTemplate.create(
                request.getName(),
                request.getDescription(),
                connectionType
        );
        
        // 根据连接类型设置连接参数
        if (connectionType == TaskType.SSE) {
            if (request.getSseServerUrl() == null) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "SSE模板必须提供serverUrl"));
            }
            template.configureSseConnection(request.getSseServerUrl());
        } else if (connectionType == TaskType.STDIO) {
            if (request.getStdioCommand() == null) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "STDIO模板必须提供command"));
            }
            template.configureStdioConnection(request.getStdioCommand());
        }
        
        // 设置默认配置
        if (request.getDefaultConfig() != null) {
            template.setDefaultConfig(request.getDefaultConfig());
        }
        
        return taskService.createTemplate(template)
                .onErrorResume(e -> {
                    log.error("创建模板失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "创建模板失败: " + e.getMessage()));
                });
    }
    
    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteTemplate(@PathVariable String id) {
        return taskService.deleteTemplate(id)
                .onErrorResume(e -> {
                    log.error("删除模板失败: {}", e.getMessage());
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "删除模板失败: " + e.getMessage()));
                });
    }
    
    /**
     * 创建模板的请求对象
     */
    @Setter
    @Getter
    public static class CreateTemplateRequest {
        private String name;
        private String description;
        private String connectionType; // "sse" 或 "stdio"
        private String sseServerUrl;   // SSE 连接URL
        private String stdioCommand;   // STDIO 命令
        private Map<String, Object> defaultConfig; // 默认配置

    }
}