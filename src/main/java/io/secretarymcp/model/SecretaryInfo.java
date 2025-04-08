package io.secretarymcp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 秘书基本信息 - 轻量级对象，只包含核心信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecretaryInfo {
    private String id;
    private String name;
    private String description;
    private boolean active;
    
    /**
     * 从完整的Secretary实体转换为SecretaryInfo
     */
    public static SecretaryInfo fromSecretary(Secretary secretary) {
        return SecretaryInfo.builder()
                .id(secretary.getId())
                .name(secretary.getName())
                .description(secretary.getDescription())
                .active(secretary.isActive())
                .build();
    }
}