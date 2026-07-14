package com.zoujuexian.aiagentdemo.core.cost;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class QuotaManager {

    private static final Logger logger = LoggerFactory.getLogger(QuotaManager.class);

    private static final long DAILY_QUOTA = 100000L;

    @Resource
    private TokenUsageTracker tokenUsageTracker;

    public boolean checkQuota(String sessionId, long requiredTokens) {
        long todayUsage = tokenUsageTracker.getTotalUsage(sessionId);
        return (todayUsage + requiredTokens) <= DAILY_QUOTA;
    }

    public String getRemainingQuota(String sessionId) {
        long todayUsage = tokenUsageTracker.getTotalUsage(sessionId);
        long remaining = DAILY_QUOTA - todayUsage;
        return "剩余配额: " + remaining + " tokens";
    }

    public long getRemainingQuotaValue(String sessionId) {
        long todayUsage = tokenUsageTracker.getTotalUsage(sessionId);
        return Math.max(0, DAILY_QUOTA - todayUsage);
    }

    public void enforceQuota(String sessionId, long requiredTokens) {
        if (!checkQuota(sessionId, requiredTokens)) {
            logger.warn("会话配额超限: sessionId={}", sessionId);
            throw new IllegalArgumentException("配额超限，请明天再试");
        }
    }
}
