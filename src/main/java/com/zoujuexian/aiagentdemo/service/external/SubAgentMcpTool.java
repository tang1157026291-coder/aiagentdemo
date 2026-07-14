package com.zoujuexian.aiagentdemo.service.external;

import com.zoujuexian.aiagentdemo.core.SubAgent;
import com.zoujuexian.aiagentdemo.core.SubAgentManager;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SubAgentMcpTool {

    private static final Logger logger = LoggerFactory.getLogger(SubAgentMcpTool.class);

    @Resource
    private SubAgentManager subAgentManager;

    @Tool(name = "create_sub_agent", description = "创建子代理，用于处理需要独立上下文的复杂任务")
    public String createSubAgent(
            @ToolParam(description = "子代理名称") String name,
            @ToolParam(description = "子代理角色定义（system prompt）") String systemPrompt
    ) {
        SubAgent agent = subAgentManager.create(name, systemPrompt);
        logger.info("创建子代理: {}", agent);
        return "子代理创建成功: " + agent.getId() + " (" + agent.getName() + ")";
    }

    @Tool(name = "chat_with_sub_agent", description = "与指定子代理对话")
    public String chatWithSubAgent(
            @ToolParam(description = "子代理 ID") String agentId,
            @ToolParam(description = "消息内容") String message
    ) {
        String response = subAgentManager.chat(agentId, message);
        logger.debug("子代理对话完成: {}", agentId);
        return response;
    }

    @Tool(name = "destroy_sub_agent", description = "销毁指定子代理")
    public String destroySubAgent(
            @ToolParam(description = "子代理 ID") String agentId
    ) {
        String result = subAgentManager.destroy(agentId);
        logger.info("销毁子代理: {}", agentId);
        return result;
    }

    @Tool(name = "list_sub_agents", description = "列出所有活跃的子代理")
    public String listSubAgents() {
        return subAgentManager.listActiveAgents();
    }
}
