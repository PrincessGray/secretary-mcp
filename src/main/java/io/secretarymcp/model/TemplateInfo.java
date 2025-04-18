package io.secretarymcp.model;

import io.secretarymcp.util.Constants.ConnectionType;
import io.secretarymcp.util.Constants.ManagementType;
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
    private ConnectionType connectionType;
    private ManagementType managementType;
    
    /**
     * 从完整的TaskTemplate实体转换为TemplateInfo
     */
    public static TemplateInfo fromTemplate(TaskTemplate template) {
        ManagementType managementType = ManagementType.NPX; // 默认值
        
        // 如果是STDIO连接类型，尝试获取管理方式
        if (template.getConnectionProfile().getConnectionType() == ConnectionType.STDIO 
                && template.getConnectionProfile().getStdioConfig() != null) {
            managementType = template.getConnectionProfile().getStdioConfig().getManagementType();
        }
        
        return TemplateInfo.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .connectionType(template.getConnectionProfile().getConnectionType())
                .managementType(managementType)
                .build();
    }
}