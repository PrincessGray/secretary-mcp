// SseConfig.java
package io.secretarymcp.model;

import lombok.Data;

@Data
public class SseConfig {
    private String serverUrl;
    private String bearerToken;
}