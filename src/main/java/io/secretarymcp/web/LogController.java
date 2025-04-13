package io.secretarymcp.web;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    private static final Logger log = LoggerFactory.getLogger(LogController.class);
    private static final String LOG_FILE_PATH = "logs/secretary-mcp.log";
    private static final Pattern LOG_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) \\[(.+?)\\] (\\w+) (.+?) - (.*)");

    @Setter
    @Getter
    static class LogEntry {
        private String timestamp;
        private String thread;
        private String level;
        private String logger;
        private String message;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<LogEntry>> getLogs(
            @RequestParam(value = "level", defaultValue = "ALL") String level,
            @RequestParam(value = "lines", defaultValue = "100") int lines) {
        
        File logFile = new File(LOG_FILE_PATH);
        if (!logFile.exists()) {
            return Mono.just(Collections.emptyList());
        }

        // 使用DataBufferUtils进行非阻塞文件读取
        return DataBufferUtils.read(Path.of(LOG_FILE_PATH), 
                new DefaultDataBufferFactory(), 8192, StandardOpenOption.READ)
            .reduce((first, second) -> {
                first.write(second);
                DataBufferUtils.release(second);
                return first;
            })
            .map(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);
                return new String(bytes, StandardCharsets.UTF_8);
            })
            .map(content -> {
                // 将内容拆分为行
                String[] allLines = content.split("\\R");
                List<LogEntry> entries = new ArrayList<>();
                
                // 取最后的指定行数
                int startIndex = Math.max(0, allLines.length - lines);
                for (int i = startIndex; i < allLines.length; i++) {
                    LogEntry entry = parseLogLine(allLines[i]);
                    if (entry != null && (level.equals("ALL") || entry.getLevel().equals(level))) {
                        entries.add(entry);
                    }
                }
                
                return entries;
            })
            .onErrorResume(e -> {
                log.error("读取日志文件失败: {}", e.getMessage(), e);
                return Mono.just(Collections.emptyList());
            });
    }

    @GetMapping("/download")
    public Mono<ResponseEntity<FileSystemResource>> downloadLog() {
        File file = new File(LOG_FILE_PATH);
        if (!file.exists()) {
            return Mono.just(ResponseEntity.notFound().build());
        }
        
        return Mono.just(ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=secretary-mcp.log")
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file)));
    }

    private LogEntry parseLogLine(String line) {
        Matcher matcher = LOG_PATTERN.matcher(line);
        if (matcher.matches()) {
            LogEntry entry = new LogEntry();
            entry.setTimestamp(matcher.group(1));
            entry.setThread(matcher.group(2));
            entry.setLevel(matcher.group(3));
            entry.setLogger(matcher.group(4));
            entry.setMessage(matcher.group(5));
            return entry;
        }
        return null;
    }
}