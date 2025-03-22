package ch.so.agi.qwc;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class ElevationController {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    private ObjectMapper objectMapper;
    private ElevationService elevationService;
    
    public ElevationController(ObjectMapper objectMapper, ElevationService elevationService) {
        this.objectMapper = objectMapper;
        this.elevationService = elevationService;
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping(@RequestHeader Map<String, String> headers, HttpServletRequest request) {
        headers.forEach((key, value) -> {
            log.info(String.format("Header '%s' = %s", key, value));
        });
        
        log.info("server name: " + request.getServerName());
        log.info("context path: " + request.getContextPath());
        
        log.info("ping"); 
        
        return new ResponseEntity<String>("qwc-elevation-service", HttpStatus.OK);
    }    
        
    @GetMapping("/getelevation")
    public ResponseEntity<?> getElevation(@RequestParam(name = "pos", required = true) String pos, 
            @RequestParam(name = "crs", required = true) String crs) throws IOException {
        
        String[] posArray = pos.split(",");
        double x = Double.valueOf(posArray[0]);
        double y = Double.valueOf(posArray[1]);
                
        double height = elevationService.getElevationByXY(x, y, crs);
        
        return new ResponseEntity<Map>(Map.of("elevation", height), HttpStatus.OK);        
    }
}
