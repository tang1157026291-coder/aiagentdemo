package com.zoujuexian.aiagentdemo.service.external;

import com.zoujuexian.aiagentdemo.service.extrenal.McpClient;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class McpReconnectManager {

    private static final Logger logger = LoggerFactory.getLogger(McpReconnectManager.class);

    @Resource
    private McpClient mcpClient;

    private static final int[] BACKOFF_SECONDS = {1, 2, 4, 8, 16};

    public void scheduleReconnect(String serverUrl) {
        logger.info("调度 MCP 重连: {}", serverUrl);
        
        for (int i = 0; i < BACKOFF_SECONDS.length; i++) {
            int finalI = i;
            int delay = BACKOFF_SECONDS[i];
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                try {
                    mcpClient.connect(serverUrl);
                    logger.info("MCP 重连成功: {}", serverUrl);
                    scheduler.shutdown();
                } catch (Exception e) {
                    logger.warn("MCP 重连失败 (第{}次): {}", finalI + 1, serverUrl);
                    if (finalI == BACKOFF_SECONDS.length - 1) {
                        scheduler.shutdown();
                    }
                }
            }, delay, TimeUnit.SECONDS);
        }
    }
}
