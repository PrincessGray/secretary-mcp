// StdioConfig.java
package io.secretarymcp.model;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.secretarymcp.util.Constants.ManagementType;

@Data
public class StdioConfig {
    private String command;
    private List<String> commandArgs;
    private Map<String, String> environmentVars;
    private ManagementType managementType = ManagementType.NPX; // 默认使用NPX
    
    // 工作目录特殊处理
    public void setWorkingDir(String dir) {
        if (environmentVars == null) {
            environmentVars = new HashMap<>();
        }
        environmentVars.put("WORKING_DIR", dir);
    }
    
    public String getWorkingDir() {
        return environmentVars != null ? environmentVars.get("WORKING_DIR") : null;
    }
}
