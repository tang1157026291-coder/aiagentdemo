package com.zoujuexian.aiagentdemo.service.external;

import com.zoujuexian.aiagentdemo.service.external.McpClient;
import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class McpHealthChecker {

    private static final Logger logger = LoggerFactory.getLogger(McpHealthChecker.class);

    @Resource
    private McpClient mcpClient;

    @Resource
    private McpReconnectManager reconnectManager;

    @PostConstruct
    public void init() {
        logger.info("MCP 健康检查器已启动");
    }

    @Scheduled(fixedRate = 30000)
    public void checkAllConnections() {
        List<McpClient.McpServerInfo> servers = mcpClient.getConnectedServers();
        
        for (McpClient.McpServerInfo server : servers) {
            if (!isHealthy(server.url())) {
                logger.warn("MCP 服务不健康: {}", server.url());
                mcpClient.disconnect(server.url());
                reconnectManager.scheduleReconnect(server.url());
            }
        }
    }

    private boolean isHealthy(String serverUrl) {
        try {
            return mcpClient.isConnected(serverUrl);
        } catch (Exception e) {
            return false;
        }
    }
}
