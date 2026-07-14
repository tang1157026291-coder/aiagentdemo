package com.zoujuexian.aiagentdemo.core.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private volatile CircuitState state = CircuitState.CLOSED;
    private static final int FAILURE_THRESHOLD = 5;
    private static final long RESET_TIMEOUT = 30000;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;

    public <T> T execute(java.util.function.Supplier<T> operation, java.util.function.Supplier<T> fallback) {
        if (state == CircuitState.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime > RESET_TIMEOUT) {
                state = CircuitState.HALF_OPEN;
                logger.info("熔断器进入半开状态");
            } else {
                logger.warn("熔断器打开，执行降级");
                return fallback.get();
            }
        }

        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            if (state == CircuitState.OPEN) {
                return fallback.get();
            }
            throw e;
        }
    }

    private void onFailure() {
        int count = failureCount.incrementAndGet();
        lastFailureTime = System.currentTimeMillis();
        if (count >= FAILURE_THRESHOLD) {
            state = CircuitState.OPEN;
            logger.error("熔断器已打开");
        }
    }

    private void onSuccess() {
        failureCount.set(0);
        state = CircuitState.CLOSED;
        logger.debug("熔断器关闭");
    }

    public CircuitState getState() {
        return state;
    }

    public enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }
}
