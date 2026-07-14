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

    public record SessionUsage(
            String sessionId,
            LocalDateTime startTime,
            Map<String, ModelUsage> modelUsages
    ) {
        public SessionUsage(String sessionId, LocalDateTime startTime) {
            this(sessionId, startTime, new HashMap<>());
        }

        public void record(String model, int promptTokens, int completionTokens) {
            ModelUsage mu = modelUsages.computeIfAbsent(model, m -> new ModelUsage(m));
            mu.promptTokens += promptTokens;
            mu.completionTokens += completionTokens;
        }

        public long getTotalUsage() {
            return modelUsages.values().stream()
                    .mapToLong(mu -> mu.promptTokens + mu.completionTokens)
                    .sum();
        }

        public UsageReport toReport() {
            return new UsageReport(sessionId, startTime, modelUsages);
        }
    }

    public record ModelUsage(String model, int promptTokens, int completionTokens) {
        public ModelUsage(String model) {
            this(model, 0, 0);
        }
    }

    public record UsageReport(
            String sessionId,
            LocalDateTime startTime,
            Map<String, ModelUsage> modelUsages
    ) {
        public long getTotalTokens() {
            return modelUsages.values().stream()
                    .mapToLong(mu -> mu.promptTokens() + mu.completionTokens())
                    .sum();
        }
    }
}
