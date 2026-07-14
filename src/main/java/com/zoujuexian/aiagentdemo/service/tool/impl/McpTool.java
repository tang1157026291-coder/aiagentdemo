package com.zoujuexian.aiagentdemo.service.tool.impl;

import com.zoujuexian.aiagentdemo.service.tool.InnerTool;
import com.zoujuexian.aiagentdemo.service.external.McpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MCP 内置工具
 * <p>
 * 启动时自动恢复持久化的 MCP 服务连接。
 * 运行时的动态增删由 McpController 直接操作 McpClientConnector 和 AiAgent。
 */
@Component
public class McpTool implements InnerTool {

    private static final Logger logger = LoggerFactory.getLogger(McpTool.class);

    private final McpClient mcpClient;

    public McpTool(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public List<ToolCallback> loadToolCallbacks() {
        List<String> savedUrls = mcpClient.getSavedUrls();
        if (savedUrls.isEmpty()) {
            logger.info("[MCP] 无持久化的 MCP 服务");
            return List.of();
        }

        logger.info("[MCP] 正在恢复 {} 个持久化的 MCP 服务...", savedUrls.size());
        List<ToolCallback> allCallbacks = new ArrayList<>();

        for (String url : savedUrls) {
            try {
                ToolCallback[] mcpCallbacks = mcpClient.connect(url);
                allCallbacks.addAll(Arrays.asList(mcpCallbacks));
                logger.info("[MCP] 已恢复: {}", url);
            } catch (Exception exception) {
                logger.error("[MCP] 恢复连接失败: {} - {}", url, exception.getMessage());
            }
        }

        return allCallbacks;
    }
}
