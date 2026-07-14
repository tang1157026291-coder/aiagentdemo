package com.zoujuexian.aiagentdemo.core.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Function;

@Component
public class TraceRecorder {

    private static final Logger logger = LoggerFactory.getLogger(TraceRecorder.class);

    public TraceSpan startSpan(String operationName) {
        TraceContext context = TraceContext.getCurrent();
        TraceSpan span = new TraceSpan();
        span.setSpanId(generateSpanId());
        span.setParentSpanId(context != null ? context.getCurrentSpanId() : null);
        span.setOperationName(operationName);
        span.setStartTime(System.currentTimeMillis());
        span.setStatus("RUNNING");
        
        if (context != null) {
            span.setTraceId(context.getTraceId());
            context.setCurrentSpanId(span.getSpanId());
        }
        
        logger.debug("开始追踪: {}", operationName);
        return span;
    }

    public void endSpan(TraceSpan span, String output, String status) {
        span.setEndTime(System.currentTimeMillis());
        span.setOutput(output);
        span.setStatus(status);
        
        long duration = span.getEndTime() - span.getStartTime();
        logger.debug("追踪结束: {} - {} ({}ms)", span.getOperationName(), status, duration);
    }

    public TraceSpan recordSpan(String operationName, String input, Function<String, String> operation) {
        TraceSpan span = startSpan(operationName);
        span.setInput(input);
        try {
            String output = operation.apply(input);
            endSpan(span, output, "SUCCESS");
            return span;
        } catch (Exception e) {
            endSpan(span, null, "FAILED");
            span.setErrorMessage(e.getMessage());
            logger.error("追踪失败: {}", operationName, e);
            throw e;
        }
    }

    private String generateSpanId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
