package io.secretarymcp.registry; // 或其他适当的包

import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import io.secretarymcp.storage.FileSystemStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

/**
 * 用户与Secretary映射注册表
 * 管理用户ID到Secretary名称的映射关系，用于工具访问控制
 */
@Component
public class UserSecretaryRegistry {
    private static final Logger log = LoggerFactory.getLogger(UserSecretaryRegistry.class);
    
    private final FileSystemStorage storage;
    
    @Autowired
    public UserSecretaryRegistry(FileSystemStorage storage) {
        this.storage = storage;
    }
    
    // 用户ID -> Secretary名称的映射
    private final Map<String, String> userSecretaryMap = new ConcurrentHashMap<>();

    
    /**
     * 注册用户与Secretary的关联
     * @param userId 用户ID
     * @param secretaryName Secretary名称
     */
    public Mono<Void> registerUserSecretary(String userId, String secretaryName) {
        if (userId == null || secretaryName == null) {
            return Mono.error(new IllegalArgumentException("用户ID和Secretary名称不能为null"));
        }
        return storage.saveUserSecretaryMapping(userId, secretaryName).then();
    }
    
    /**
     * 获取用户关联的Secretary名称
     * @param userId 用户ID
     * @return Secretary名称，如果用户未注册则返回null
     */
    public Mono<String> getSecretaryForUser(String userId) {
        return storage.getSecretaryNameByUserId(userId);
    }
    
    /**
     * 同步方法获取用户关联的Secretary名称，用于非响应式上下文
     * @param userId 用户ID
     * @return Secretary名称，如果用户未注册则返回null
     */
    public String getSecretaryForUserSync(String userId) {
        return storage.getSecretaryNameByUserId(userId).block();
    }
    
    /**
     * 移除用户与Secretary的关联
     * @param userId 用户ID
     * @return 操作完成的Mono
     */
    public Mono<Void> unregisterUser(String userId) {
        return storage.deleteUserSecretaryMapping(userId).then();
    }
    
    /**
     * 获取Secretary的工具名称前缀
     * @param secretaryName Secretary名称
     * @return 工具名称前缀
     */
    public String getToolPrefix(String secretaryName) {
        return secretaryName + "_"; // 注意：工具全名格式为 secretaryname_taskname_toolname
    }
    
    /**
     * 判断工具是否属于特定Secretary
     * @param toolName 工具名称（格式：secretaryname_taskname_toolname）
     * @param secretaryName Secretary名称
     * @return 如果工具属于指定的Secretary则返回true
     */
    public boolean isToolBelongsToSecretary(String toolName, String secretaryName) {
        String prefix = getToolPrefix(secretaryName);
        return toolName != null && toolName.startsWith(prefix);
    }
    
    /**
     * 从工具名称中解析Secretary名称
     * @param toolName 工具名称（格式：secretaryname_taskname_toolname）
     * @return Secretary名称，如果格式不正确则返回null
     */
    public String extractSecretaryFromToolName(String toolName) {
        if (toolName == null || !toolName.contains("_")) {
            return null;
        }
        return toolName.split("_", 2)[0]; // 获取第一个下划线前的内容
    }
    
    /**
     * 从工具名称中解析Task名称
     * @param toolName 工具名称（格式：secretaryname_taskname_toolname）
     * @return Task名称，如果格式不正确则返回null
     */
    public String extractTaskFromToolName(String toolName) {
        if (toolName == null || toolName.split("_").length < 3) {
            return null;
        }
        return toolName.split("_", 3)[1]; // 获取第一个和第二个下划线之间的内容
    }
    
    /**
     * 从工具名称中解析原始工具名称
     * @param toolName 工具名称（格式：secretaryname_taskname_toolname）
     * @return 原始工具名称，如果格式不正确则返回null
     */
    public String extractOriginalToolName(String toolName) {
        if (toolName == null || toolName.split("_").length < 3) {
            return null;
        }
        String[] parts = toolName.split("_", 3);
        return parts[2]; // 获取第二个下划线后的内容
    }
    
    /**
     * 检查用户是否可以访问指定工具
     * @param userId 用户ID
     * @param toolName 工具名称（格式：secretaryname_taskname_toolname）
     * @return 如果用户可以访问该工具则返回true
     */
    public boolean canUserAccessTool(String userId, String toolName) {
        // 如果工具名称不符合格式要求，不允许访问
        if (toolName == null || toolName.split("_").length < 3) {
            return false;
        }
        
        // 从工具名称提取Secretary名称
        String secretaryFromTool = extractSecretaryFromToolName(toolName);
        
        // 如果无法提取Secretary名称，不允许访问
        if (secretaryFromTool == null) {
            return false;
        }
        
        // 获取用户关联的Secretary
        String userSecretary = getSecretaryForUserSync(userId);
        
        // 如果用户未关联Secretary，不允许访问任何工具
        if (userSecretary == null) {
            return false;
        }
        
        // 检查工具的Secretary是否与用户的Secretary匹配
        return userSecretary.equals(secretaryFromTool);
    }
    
    /**
     * 构建完整的工具名称
     * @param secretaryName Secretary名称
     * @param taskName Task名称
     * @param toolName 原始工具名称
     * @return 格式化的完整工具名称
     */
    public String buildFullToolName(String secretaryName, String taskName, String toolName) {
        return secretaryName + "_" + taskName + "_" + toolName;
    }
    
    /**
     * 从会话ID中提取用户ID
     * 格式为：userId_uuid
     */
    public String extractUserIdFromSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            log.warn("会话ID为空，无法提取用户ID");
            return null;
        }
        
        // 分割会话ID，提取用户ID部分（使用下划线分隔）
        int underscoreIndex = sessionId.indexOf('_');
        if (underscoreIndex > 0) {
            String userId = sessionId.substring(0, underscoreIndex);
            log.info("从会话ID[{}]成功提取用户ID: {}", sessionId, userId);
            return userId;
        } else {
            log.warn("会话ID[{}]格式不正确，无法提取用户ID", sessionId);
            return null;
        }
    }
    
    /**
     * 获取所有注册的用户ID
     */
    public Set<String> getAllRegisteredUsers() {
        return new HashSet<>(userSecretaryMap.keySet());
    }
    
    /**
     * 获取所有Secretary名称
     */
    public Set<String> getAllSecretaries() {
        return new HashSet<>(userSecretaryMap.values());
    }
}