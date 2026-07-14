package com.zoujuexian.aiagentdemo.core;

import com.zoujuexian.aiagentdemo.config.MemoryProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final Cache<String, ChatMemory> sessionCache;

    @Resource
    private MemoryProperties memoryProperties;

    public SessionManager() {
        this.sessionCache = Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(1000)
                .removalListener((sessionId, memory, cause) -> 
                    logger.info("会话已过期或移除: sessionId={}, cause={}", sessionId, cause)
                )
                .build();
    }

    public ChatMemory getOrCreate(String sessionId, ChatClient chatClient) {
        return sessionCache.get(sessionId, id -> {
            ChatMemory memory = ChatMemory.forMainAgent(chatClient);
            logger.debug("创建新会话: sessionId={}", sessionId);
            return memory;
        });
    }

    public ChatMemory get(String sessionId) {
        return sessionCache.getIfPresent(sessionId);
    }

    public void put(String sessionId, ChatMemory memory) {
        sessionCache.put(sessionId, memory);
    }

    public void invalidate(String sessionId) {
        sessionCache.invalidate(sessionId);
        logger.info("手动清除会话: sessionId={}", sessionId);
    }

    public void invalidateAll() {
        sessionCache.invalidateAll();
        logger.info("清除所有会话");
    }

    public long getSessionCount() {
        return sessionCache.estimatedSize();
    }

    public boolean exists(String sessionId) {
        return sessionCache.getIfPresent(sessionId) != null;
    }
}
