package com.zoujuexian.aiagentdemo.core.cost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenUsageTracker {

    private static final Logger logger = LoggerFactory.getLogger(TokenUsageTracker.class);

    private final Map<String, SessionUsage> sessionUsages = new ConcurrentHashMap<>();

    public void recordUsage(String sessionId, String model, int promptTokens, int completionTokens) {
        SessionUsage usage = sessionUsages.computeIfAbsent(sessionId,
                id -> new SessionUsage(id, LocalDateTime.now()));
        usage.record(model, promptTokens, completionTokens);
        
        logger.debug("记录 Token 使用: sessionId={}, model={}, prompt={}, completion={}", 
                sessionId, model, promptTokens, completionTokens);
    }

    public UsageReport getReport(String sessionId) {
        SessionUsage usage = sessionUsages.get(sessionId);
        return usage != null ? usage.toReport() : null;
    }

    public long getTotalUsage(String sessionId) {
        SessionUsage usage = sessionUsages.get(sessionId);
        return usage != null ? usage.getTotalUsage() : 0;
    }

    public void clearSession(String sessionId) {
        sessionUsages.remove(sessionId);
    }

    public long getActiveSessionCount() {
        return sessionUsages.size();
    }

    public static class SessionUsage {
        private final String sessionId;
        private final LocalDateTime startTime;
        private final Map<String, ModelUsage> modelUsages = new HashMap<>();

        public SessionUsage(String sessionId, LocalDateTime startTime) {
            this.sessionId = sessionId;
            this.startTime = startTime;
        }

        public void record(String model, int promptTokens, int completionTokens) {
            ModelUsage mu = modelUsages.computeIfAbsent(model, m -> new ModelUsage(m));
            mu.addTokens(promptTokens, completionTokens);
        }

        public long getTotalUsage() {
            return modelUsages.values().stream()
                    .mapToLong(mu -> mu.getPromptTokens() + mu.getCompletionTokens())
                    .sum();
        }

        public UsageReport toReport() {
            return new UsageReport(sessionId, startTime, modelUsages);
        }

        public String getSessionId() { return sessionId; }
        public LocalDateTime getStartTime() { return startTime; }
        public Map<String, ModelUsage> getModelUsages() { return modelUsages; }
    }

    public static class ModelUsage {
        private final String model;
        private int promptTokens;
        private int completionTokens;

        public ModelUsage(String model) {
            this.model = model;
            this.promptTokens = 0;
            this.completionTokens = 0;
        }

        public void addTokens(int prompt, int completion) {
            this.promptTokens += prompt;
            this.completionTokens += completion;
        }

        public String getModel() { return model; }
        public int getPromptTokens() { return promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
    }

    public record UsageReport(
            String sessionId,
            LocalDateTime startTime,
            Map<String, ModelUsage> modelUsages
    ) {
        public long getTotalTokens() {
            return modelUsages.values().stream()
                    .mapToLong(mu -> mu.getPromptTokens() + mu.getCompletionTokens())
                    .sum();
        }
    }
}
