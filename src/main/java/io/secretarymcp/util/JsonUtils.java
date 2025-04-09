package io.secretarymcp.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * JSON处理工具类
 */
public class JsonUtils {
    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);

    // 默认虚拟线程调度器
    private static final Scheduler DEFAULT_VIRTUAL_SCHEDULER = Schedulers.fromExecutor(
        Executors.newVirtualThreadPerTaskExecutor()
    );

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
     * 异步保存对象到JSON文件（使用默认调度器）
     */
    public static <T> Mono<Void> saveToFileAsync(T object, Path filePath) {
        return saveToFileAsync(object, filePath, DEFAULT_VIRTUAL_SCHEDULER);
    }
    
    /**
     * 异步保存对象到JSON文件（指定调度器）
     */
    public static <T> Mono<Void> saveToFileAsync(T object, Path filePath, Scheduler scheduler) {
        return Mono.fromCallable(() -> {
                // 确保目录存在
                Files.createDirectories(filePath.getParent());
                
                // 将对象序列化并写入文件
                String json = objectMapper.writeValueAsString(object);
                Files.writeString(filePath, json);
                
                log.debug("保存文件成功: {}", filePath);
                return true;
            })
            .subscribeOn(scheduler)
            .doOnError(e -> log.error("保存文件失败: {}", filePath, e))
            .then();
    }
    
    /**
     * 异步从JSON文件加载对象（使用默认调度器）
     */
    public static <T> Mono<T> loadFromFileAsync(Path filePath, Class<T> clazz) {
        return loadFromFileAsync(filePath, clazz, DEFAULT_VIRTUAL_SCHEDULER);
    }
    
    /**
     * 异步从JSON文件加载对象（指定调度器）
     */
    public static <T> Mono<T> loadFromFileAsync(Path filePath, Class<T> clazz, Scheduler scheduler) {
        return Mono.fromCallable(() -> {
                if (!Files.exists(filePath)) {
                    log.debug("文件不存在: {}", filePath);
                    return null;
                }
                
                // 读取文件并反序列化
                String content = Files.readString(filePath);
                T result = objectMapper.readValue(content, clazz);
                
                log.debug("加载文件成功: {}", filePath);
                return result;
            })
            .subscribeOn(scheduler)
            .doOnError(e -> log.error("加载文件失败: {}", filePath, e));
    }
    
    /**
     * 检查文件是否存在（异步，使用默认调度器）
     */
    public static Mono<Boolean> existsAsync(Path filePath) {
        return existsAsync(filePath, DEFAULT_VIRTUAL_SCHEDULER);
    }
    
    /**
     * 检查文件是否存在（异步，指定调度器）
     */
    public static Mono<Boolean> existsAsync(Path filePath, Scheduler scheduler) {
        return Mono.fromCallable(() -> Files.exists(filePath))
                .subscribeOn(scheduler);
    }
    
    /**
     * 异步删除文件（使用默认调度器）
     */
    public static Mono<Void> deleteFileAsync(Path filePath) {
        return deleteFileAsync(filePath, DEFAULT_VIRTUAL_SCHEDULER);
    }
    
    /**
     * 异步删除文件（指定调度器）
     */
    public static Mono<Void> deleteFileAsync(Path filePath, Scheduler scheduler) {
        return Mono.fromCallable(() -> {
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.debug("删除文件成功: {}", filePath);
                }
                return null;
            })
            .subscribeOn(scheduler)
            .doOnError(e -> log.error("删除文件失败: {}", filePath, e))
            .then();
    }
    
    /**
     * 异步读取文件内容为字符串（使用默认调度器）
     */
    public static Mono<String> readFileAsStringAsync(Path filePath) {
        return readFileAsStringAsync(filePath, DEFAULT_VIRTUAL_SCHEDULER);
    }
    
    /**
     * 异步读取文件内容为字符串（指定调度器）
     */
    public static Mono<String> readFileAsStringAsync(Path filePath, Scheduler scheduler) {
        return Mono.fromCallable(() -> {
                if (!Files.exists(filePath)) {
                    log.debug("文件不存在: {}", filePath);
                    return null;
                }
                
                // 读取文件内容
                String content = Files.readString(filePath);
                
                log.debug("读取文件成功: {}", filePath);
                return content;
            })
            .subscribeOn(scheduler)
            .doOnError(e -> log.error("读取文件失败: {}", filePath, e));
    }
    
    /**
     * 异步写入字符串到文件（使用默认调度器）
     */
    public static Mono<Void> writeFileAsync(String content, Path filePath) {
        return writeFileAsync(content, filePath, DEFAULT_VIRTUAL_SCHEDULER);
    }
    
    /**
     * 异步写入字符串到文件（指定调度器）
     */
    public static Mono<Void> writeFileAsync(String content, Path filePath, Scheduler scheduler) {
        return Mono.fromCallable(() -> {
                // 确保目录存在
                Files.createDirectories(filePath.getParent());
                
                // 写入文件
                Files.writeString(filePath, content);
                
                log.debug("写入文件成功: {}", filePath);
                return true;
            })
            .subscribeOn(scheduler)
            .doOnError(e -> log.error("写入文件失败: {}", filePath, e))
            .then();
    }
}