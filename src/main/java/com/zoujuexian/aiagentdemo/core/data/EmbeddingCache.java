package com.zoujuexian.aiagentdemo.core.data;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Component
public class EmbeddingCache {

    private final Cache<String, float[]> embeddingCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    public float[] get(String text) {
        String hash = hashText(text);
        return embeddingCache.getIfPresent(hash);
    }

    public void put(String text, float[] embedding) {
        String hash = hashText(text);
        embeddingCache.put(hash, embedding);
    }

    public boolean contains(String text) {
        String hash = hashText(text);
        return embeddingCache.asMap().containsKey(hash);
    }

    public void invalidate(String text) {
        String hash = hashText(text);
        embeddingCache.invalidate(hash);
    }

    public void invalidateAll() {
        embeddingCache.invalidateAll();
    }

    public long size() {
        return embeddingCache.estimatedSize();
    }

    private String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }
}
