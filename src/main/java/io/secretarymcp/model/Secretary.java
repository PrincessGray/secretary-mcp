package io.secretarymcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 秘书类，负责管理一组相关任务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Secretary {
    // 基本信息
    private String id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // 任务ID集合
    private Set<String> taskIds;
    
    // 元数据
    private boolean active;
    
    /**
     * 创建新的秘书实例
     */
    public static Secretary create(String name, String description) {
        return Secretary.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description != null ? description : "")
                .taskIds(new HashSet<>())
                .active(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * 激活秘书
     */
    public void activate() {
        this.active = true;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 停用秘书
     */
    public void deactivate() {
        this.active = false;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 添加任务
     */
    public void addTask(String taskId) {
        if (this.taskIds == null) {
            this.taskIds = new HashSet<>();
        }
        this.taskIds.add(taskId);
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 移除任务
     */
    public void removeTask(String taskId) {
        if (this.taskIds != null) {
            this.taskIds.remove(taskId);
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 获取任务ID列表
     */
    public Set<String> getTaskIds() {
        return taskIds != null ? new HashSet<>(taskIds) : new HashSet<>();
    }
    
    /**
     * 判断是否包含指定任务
     */
    public boolean hasTask(String taskId) {
        return taskIds != null && taskIds.contains(taskId);
    }

        /**
     * 从完整的Secretary实体转换为SecretaryInfo
     */
}