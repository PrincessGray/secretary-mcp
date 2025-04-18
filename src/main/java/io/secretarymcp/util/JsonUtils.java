package io.secretarymcp.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

/**
 * JSON处理工具类 - 完全非阻塞版本
 */
public class JsonUtils {
    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);
    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();
    private static final int BUFFER_SIZE = 16384; // 16KB 缓冲区
    private static final Scheduler ELASTIC_SCHEDULER = Schedulers.boundedElastic();

    /**
     * -- GETTER --
     *  获取共享的ObjectMapper实例
     */
    // 预配置的ObjectMapper实例
    @Getter
    private static final ObjectMapper objectMapper = createObjectMapper();
    
    /**
     * 创建预配置的ObjectMapper
     */
    public static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 注册Java 8日期时间模块
        mapper.registerModule(new JavaTimeModule());
        
        // 配置序列化选项
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        
        return mapper;
    }

    /**
     * 将对象转换为JSON字符串
     */
    public static <T> String toJson(T object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("序列化对象失败: {}", e.getMessage(), e);
            throw new RuntimeException("序列化对象失败", e);
        }
    }
    
    /**
     * 将JSON字符串转换为对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("反序列化JSON失败: {}", e.getMessage(), e);
            throw new RuntimeException("反序列化JSON失败", e);
        }
    }
    
    /**
     * 将JSON字符串转换为Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonToMap(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("JSON转Map失败: {}", e.getMessage(), e);
            throw new RuntimeException("JSON转Map失败", e);
        }
    }
    
    /**
     * 将JSON字符串转换为对象，使用TypeReference
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            log.error("反序列化JSON失败: {}", e.getMessage(), e);
            throw new RuntimeException("反序列化JSON失败", e);
        }
    }
    
    /**
     * 非阻塞保存对象到JSON文件
     */
    public static <T> Mono<Void> saveToFileAsync(T object, Path filePath) {
        return Mono.fromCallable(() -> {
                try {
                    return objectMapper.writeValueAsString(object);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("序列化对象失败", e);
                }
            })
            .flatMap(json -> writeFileAsync(json, filePath))
            .doOnError(e -> log.error("保存文件失败: {}", filePath, e));
    }
    
    /**
     * 非阻塞从JSON文件加载对象
     */
    public static <T> Mono<T> loadFromFileAsync(Path filePath, Class<T> clazz) {
        return readFileAsStringAsync(filePath)
            .flatMap(content -> {
                try {
                    if (content == null) {
                        return Mono.empty();
                    }
                    return Mono.just(objectMapper.readValue(content, clazz));
                } catch (Exception e) {
                    return Mono.error(new RuntimeException("反序列化JSON失败", e));
                }
            })
            .doOnError(e -> log.error("加载文件失败: {}", filePath, e));
    }
    
    /**
     * 检查文件是否存在（非阻塞）
     */
    public static Mono<Boolean> existsAsync(Path filePath) {
        return Mono.fromCallable(() -> Files.exists(filePath))
            .subscribeOn(ELASTIC_SCHEDULER);
    }
    
    /**
     * 确保目录存在（非阻塞）
     */
    public static Mono<Void> ensureDirectoryAsync(Path directory) {
        return Mono.fromCallable(() -> {
                Files.createDirectories(directory);
                return true;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .then();
    }
    
    /**
     * 非阻塞删除文件
     */
    public static Mono<Void> deleteFileAsync(Path filePath) {
        return Mono.fromCallable(() -> {
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.debug("删除文件成功: {}", filePath);
                }
                return true;
            })
            .subscribeOn(ELASTIC_SCHEDULER)
            .onErrorResume(e -> {
                log.error("删除文件失败: {}", filePath, e);
                return Mono.just(false);
            })
            .then();
    }
    
    /**
     * 非阻塞读取文件内容为字符串
     */
    public static Mono<String> readFileAsStringAsync(Path filePath) {
        return existsAsync(filePath)
            .flatMap(exists -> {
                if (!exists) {
                    log.debug("文件不存在: {}", filePath);
                    return Mono.empty();
                }
                
                // 使用弹性线程池执行文件读取
                return Mono.fromCallable(() -> {
                        return Files.readString(filePath);
                    })
                    .subscribeOn(ELASTIC_SCHEDULER)
                    .doOnSuccess(content -> log.debug("读取文件成功: {}", filePath));
            })
            .onErrorResume(e -> {
                if (e instanceof NoSuchFileException) {
                    return Mono.empty();
                }
                log.error("读取文件失败: {}", filePath, e);
                return Mono.error(e);
            });
    }
    
    /**
     * 非阻塞写入字符串到文件
     */
    public static Mono<Void> writeFileAsync(String content, Path filePath) {
        return ensureDirectoryAsync(filePath.getParent())
            .then(Mono.fromCallable(() -> {
                    Files.writeString(filePath, content);
                    return true;
                })
                .subscribeOn(ELASTIC_SCHEDULER)
                .doOnSuccess(v -> log.debug("写入文件成功: {}", filePath))
                .then());
    }
}