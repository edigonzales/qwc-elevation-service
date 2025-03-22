package ch.so.agi.qwc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class RuntimeConfig {
    private Logger log = LoggerFactory.getLogger(this.getClass());
    
    @Value("${app.configPath}")
    private String configPath;
    
    @Value("${app.serviceName}")
    private String serviceName;
    
    private ObjectMapper objectMapper;
    
    private JsonNode configNode;
            
    public RuntimeConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    public String get(String name) {
        if (configNode == null) {
            try {
                Path filename = Paths.get(configPath, serviceName + "Config.json");
                String json = new String(Files.readAllBytes(filename));
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(json);
                configNode = rootNode.path("config");                
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        return configNode.has(name) ? configNode.get(name).asText() : null;        
    }
}
