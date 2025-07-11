package io.secretarymcp.util;

import lombok.Getter;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 系统常量定义
 */
public class Constants {
    
    // 任务状态
    @Getter
    public enum TaskStatus {
        INACTIVE("inactive"),
        ACTIVE("active"),
        ERROR("error");
        
        private final String value;
        
        TaskStatus(String value) {
            this.value = value;
        }

        public static TaskStatus fromValue(String value) {
            for (TaskStatus status : TaskStatus.values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return INACTIVE; // 默认状态
        }
    }
    
    // 连接类型（统一定义，替代TaskType）
    @Getter
    public enum ConnectionType {
        SSE("sse"),        // Server-Sent Events 通信
        STDIO("stdio");    // 标准输入输出通信
        
        private final String value;
        
        ConnectionType(String value) {
            this.value = value;
        }

        public static ConnectionType fromValue(String value) {
            for (ConnectionType type : ConnectionType.values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return SSE; // 默认使用SSE
        }
    }
    
    // 包管理方式
    @Getter
    public enum ManagementType {
        NPX("npx"),        // 使用NPX管理
        UVX("uvx");        // 使用UVX管理
        
        
        private final String value;
        
        ManagementType(String value) {
            this.value = value;
        }

        public static ManagementType fromValue(String value) {
            for (ManagementType type : ManagementType.values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return NPX; // 默认使用NPX
        }
    }
    
    // 文件路径常量
    public static class PathsUtil {
        // 秘书相关路径
        public static Path getSecretaryDir(String baseDir) {
            return Paths.get(baseDir, "secretaries");
        }
        
        public static Path getSecretaryFile(String baseDir, String secretaryId) {
            return Paths.get(baseDir, "secretaries", secretaryId, "secretary.json");
        }

        // 任务相关路径
        public static Path getTasksDir(String baseDir, String secretaryId) {
            return Paths.get(baseDir, "secretaries", secretaryId, "tasks");
        }
        
        public static Path getTaskFile(String baseDir, String secretaryId, String taskId) {
            return Paths.get(baseDir, "secretaries", secretaryId, "tasks", taskId, "task.json");
        }
        
        public static Path getTaskWorkDir(String baseDir, String secretaryId, String taskId) {
            return Paths.get(baseDir, "secretaries", secretaryId, "tasks", taskId);
        }
        
        // 模板相关路径
        public static Path getTemplatesDir(String baseDir) {
            return Paths.get(baseDir, "templates");
        }
        
        public static Path getTemplateFile(String baseDir, String templateId) {
            return Paths.get(baseDir, "templates", templateId, "template.json");
        }
    }
    
    // MCP相关常量
    public static class Mcp {
        public static final String SERVER_NAME = "MCP Secretary System";
        public static final String CLIENT_NAME = "MCP Secretary Client";
        public static final String VERSION = "0.1.0";
        
        // 工具名称前缀，避免冲突
        public static final String TOOL_PREFIX = "mcp.";
    }
}