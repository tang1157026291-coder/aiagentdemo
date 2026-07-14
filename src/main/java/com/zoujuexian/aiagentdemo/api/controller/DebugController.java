package com.zoujuexian.aiagentdemo.api.controller;

import com.zoujuexian.aiagentdemo.api.controller.dto.ChatResponse;
import com.zoujuexian.aiagentdemo.core.debug.DebugService;
import com.zoujuexian.aiagentdemo.core.debug.DebugService.DebugRecord;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/debug", produces = MediaType.APPLICATION_JSON_VALUE)
public class DebugController {

    @Resource
    private DebugService debugService;

    @GetMapping("/status")
    public Map<String, Object> getDebugStatus() {
        return Map.of(
                "enabled", debugService.isDebugEnabled()
        );
    }

    @PostMapping("/toggle")
    public Map<String, Object> toggleDebug() {
        debugService.toggleDebug();
        return Map.of(
                "enabled", debugService.isDebugEnabled()
        );
    }

    @PostMapping("/enable")
    public Map<String, Object> enableDebug() {
        debugService.setDebugEnabled(true);
        return Map.of("enabled", true);
    }

    @PostMapping("/disable")
    public Map<String, Object> disableDebug() {
        debugService.setDebugEnabled(false);
        return Map.of("enabled", false);
    }

    @GetMapping("/records/{sessionId}")
    public List<DebugRecord> getDebugRecords(@PathVariable String sessionId) {
        return debugService.getDebugRecords(sessionId);
    }

    @PostMapping("/clear/{sessionId}")
    public ChatResponse clearRecords(@PathVariable String sessionId) {
        debugService.clearRecords(sessionId);
        return ChatResponse.ok("调试记录已清除");
    }
}
