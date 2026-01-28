package com.Finn.everything_app;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
public class TestController {

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Hello from Java Backend!");
        response.put("timestamp", LocalDateTime.now());
        response.put("status", "OK");
        return response;
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("received", request);
        response.put("echo", "You sent: " + request.get("message"));
        return response;
    }
}