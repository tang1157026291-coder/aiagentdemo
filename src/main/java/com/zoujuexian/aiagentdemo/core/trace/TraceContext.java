package com.zoujuexian.aiagentdemo.core.trace;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TraceContext {

    private final String traceId;
    private final String sessionId;
    private String currentSpanId;
    private final Map<String, String> metadata = new HashMap<>();

    private static final ThreadLocal<TraceContext> currentContext = new ThreadLocal<>();

    public TraceContext(String sessionId) {
        this.traceId = UUID.randomUUID().toString();
        this.sessionId = sessionId;
    }

    public static TraceContext start(String sessionId) {
        TraceContext context = new TraceContext(sessionId);
        currentContext.set(context);
        return context;
    }

    public static TraceContext getCurrent() {
        return currentContext.get();
    }

    public static void clear() {
        currentContext.remove();
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCurrentSpanId() {
        return currentSpanId;
    }

    public void setCurrentSpanId(String currentSpanId) {
        this.currentSpanId = currentSpanId;
    }

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}
