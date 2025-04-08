package io.secretarymcp.model;

import io.secretarymcp.util.Constants.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板基本信息 - 轻量级对象，只包含核心信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateInfo {
    private String id;
    private String name;
    private String description;
    private TaskType connectionType;
    
    /**
     * 从完整的TaskTemplate实体转换为TemplateInfo
     */
    public static TemplateInfo fromTemplate(TaskTemplate template) {
        return TemplateInfo.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .connectionType(template.getConnectionType())
                .build();
    }
}