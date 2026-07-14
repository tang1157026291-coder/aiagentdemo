package com.zoujuexian.aiagentdemo.core.debug;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DebugService {

    private volatile boolean debugEnabled = false;

    private final Map<String, List<DebugRecord>> debugRecords = new ConcurrentHashMap<>();

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    public void toggleDebug() {
        this.debugEnabled = !this.debugEnabled;
    }

    public void recordPrompt(String sessionId, String prompt) {
        if (!debugEnabled) return;
        getRecords(sessionId).add(new DebugRecord(
                "PROMPT", prompt, null, LocalDateTime.now()
        ));
    }

    public void recordResult(String sessionId, String result) {
        if (!debugEnabled) return;
        getRecords(sessionId).add(new DebugRecord(
                "RESULT", null, result, LocalDateTime.now()
        ));
    }

    public void recordToolCall(String sessionId, String toolName, String input, String output) {
        if (!debugEnabled) return;
        getRecords(sessionId).add(new DebugRecord(
                "TOOL_CALL", input, output, LocalDateTime.now()
        ));
    }

    public List<DebugRecord> getDebugRecords(String sessionId) {
        return debugRecords.getOrDefault(sessionId, List.of());
    }

    public void clearRecords(String sessionId) {
        debugRecords.remove(sessionId);
    }

    private List<DebugRecord> getRecords(String sessionId) {
        return debugRecords.computeIfAbsent(sessionId, id -> new ArrayList<>());
    }

    public static class DebugRecord {
        private final String type;
        private final String prompt;
        private final String result;
        private final LocalDateTime timestamp;

        public DebugRecord(String type, String prompt, String result, LocalDateTime timestamp) {
            this.type = type;
            this.prompt = prompt;
            this.result = result;
            this.timestamp = timestamp;
        }

        public String getType() { return type; }
        public String getPrompt() { return prompt; }
        public String getResult() { return result; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
