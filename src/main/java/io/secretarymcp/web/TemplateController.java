package io.secretarymcp.web;

import io.secretarymcp.model.*;
import io.secretarymcp.service.TaskService;
import io.secretarymcp.util.Constants;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        Constants.ConnectionType connectionType;
        try {
            connectionType = Constants.ConnectionType.fromValue(request.getConnectionType());
        } catch (Exception e) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "无效的连接类型: " + request.getConnectionType()));
        }
        
        TaskTemplate template;
        
        // 根据连接类型创建不同的模板
        if (connectionType == Constants.ConnectionType.SSE) {
            if (request.getSseServerUrl() == null) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "SSE模板必须提供serverUrl"));
            }
            
            // 使用工厂方法创建SSE模板
            template = TaskTemplate.createSseTemplate(
                    request.getName(),
                    request.getDescription(),
                    request.getSseServerUrl()
            );
            
            // 设置其他SSE特定参数
            SseConfig sseConfig = template.getConnectionProfile().getSseConfig();
            if (request.getSseAuthToken() != null) {
                sseConfig.setAuthToken(request.getSseAuthToken());
            }
            
            if (request.getSseHeaders() != null) {
                sseConfig.setCustomHeaders(request.getSseHeaders());
            }
            
        } else if (connectionType == Constants.ConnectionType.STDIO) {
            if (request.getStdioCommand() == null) {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "STDIO模板必须提供command"));
            }
            
            // 使用工厂方法创建STDIO模板
            template = TaskTemplate.createStdioTemplate(
                    request.getName(),
                    request.getDescription(),
                    request.getStdioCommand()
            );
            
            // 设置其他STDIO特定参数
            StdioConfig stdioConfig = template.getConnectionProfile().getStdioConfig();
            if (request.getStdioArgs() != null) {
                stdioConfig.setCommandArgs(request.getStdioArgs());
            }
            
            if (request.getStdioEnv() != null) {
                stdioConfig.setEnvironmentVars(request.getStdioEnv());
            }
        } else {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "不支持的连接类型: " + connectionType));
        }
        
        // 设置通用配置
        GeneralConfig generalConfig = template.getConnectionProfile().getGeneralConfig();
        if (generalConfig == null) {
            generalConfig = new GeneralConfig();
            template.getConnectionProfile().setGeneralConfig(generalConfig);
        }
        
        if (request.getTimeoutSeconds() != null) {
            generalConfig.setTimeoutSeconds(request.getTimeoutSeconds());
        }
        
        if (request.getEnableRoots() != null) {
            generalConfig.setEnableRoots(request.getEnableRoots());
        }
        
        if (request.getEnableSampling() != null) {
            generalConfig.setEnableSampling(request.getEnableSampling());
        }
        
        // 设置其他自定义配置
        if (request.getCustomSettings() != null) {
            for (Map.Entry<String, Object> entry : request.getCustomSettings().entrySet()) {
                generalConfig.setCustomSetting(entry.getKey(), entry.getValue());
            }
        }
        
        // 设置默认配置
        if (request.getDefaultConfig() != null) {
            for (Map.Entry<String, Object> entry : request.getDefaultConfig().entrySet()) {
                template.setDefaultConfigValue(entry.getKey(), entry.getValue());
            }
        }
        
        // 添加可定制参数
        if (request.getCustomizableParams() != null) {
            for (CustomizableParamRequest param : request.getCustomizableParams()) {
                switch (param.getCategory()) {
                    case STDIO_ENV:
                        template.addCustomizableEnvParam(
                            param.getName(),
                            param.getDisplayName(),
                            param.getDescription(),
                            (String) param.getDefaultValue(),
                            param.isRequired()
                        );
                        break;
                    case STDIO_ARG:
                        boolean enabledByDefault = false;
                        if (param.getDefaultValue() != null) {
                            if (param.getDefaultValue() instanceof Boolean) {
                                enabledByDefault = (Boolean) param.getDefaultValue();
                            } else {
                                enabledByDefault = Boolean.parseBoolean(param.getDefaultValue().toString());
                            }
                        }
                        
                        template.addCustomizableArgParam(
                            param.getName(),
                            param.getDisplayName(),
                            param.getDescription(),
                            enabledByDefault,
                            param.isRequired()
                        );
                        break;
                    case SSE_AUTH_PARAM:
                        template.addSseAuthParam(
                            param.getName(),
                            param.getDisplayName(),
                            param.getDescription(),
                            (String) param.getDefaultValue(),
                            param.isRequired()
                        );
                        break;
                }
            }
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
        
        // SSE特定参数
        private String sseServerUrl;
        private String sseAuthToken;
        private Map<String, String> sseHeaders;
        
        // STDIO特定参数
        private String stdioCommand;
        private List<String> stdioArgs;
        private Map<String, String> stdioEnv;
        
        // 通用配置参数
        private Integer timeoutSeconds;
        private Boolean enableRoots;
        private Boolean enableSampling;
        private Map<String, Object> customSettings;
        
        // 其他配置
        private Map<String, Object> defaultConfig;
        
        // 可定制参数
        private List<CustomizableParamRequest> customizableParams;
    }
    
    /**
     * 可定制参数请求对象
     */
    @Setter
    @Getter
    public static class CustomizableParamRequest {
        private String name;
        private String displayName;
        private String description;
        private String type;
        private boolean required;
        private Object defaultValue;
        private TaskTemplate.ConfigParam.ConfigParamCategory category;
    }
}