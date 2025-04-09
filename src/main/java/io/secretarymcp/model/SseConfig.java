// SseConfig.java
package io.secretarymcp.model;

import lombok.Data;
import java.util.Map;

@Data
public class SseConfig {
    private String serverUrl;
    private String authToken;
    private Map<String, String> customHeaders;
}