package com.zoujuexian.aiagentdemo.core.workflow;

import com.zoujuexian.aiagentdemo.api.exception.AgentException;
import com.zoujuexian.aiagentdemo.api.exception.ErrorCode;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class WorkflowEngine {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowEngine.class);

    @Resource
    private ChatClient chatClient;

    public WorkflowState execute(WorkflowDefinition definition) {
        WorkflowState state = new WorkflowState();
        Set<String> visited = new HashSet<>();

        String currentNodeId = definition.getStartNodeId();

        while (currentNodeId != null) {
            if (visited.contains(currentNodeId)) {
                throw new AgentException(ErrorCode.WORKFLOW_ERROR, "工作流存在循环: " + currentNodeId);
            }
            visited.add(currentNodeId);

            WorkflowNode node = definition.getNode(currentNodeId);
            if (node == null) {
                throw new AgentException(ErrorCode.WORKFLOW_ERROR, "未找到节点: " + currentNodeId);
            }

            logger.debug("执行节点: {}", currentNodeId);
            state.setCurrentNodeId(currentNodeId);

            try {
                state = node.execute(state, chatClient);

                if (node.isTerminal()) {
                    logger.debug("到达终止节点: {}", currentNodeId);
                    break;
                }

                currentNodeId = findNextNode(definition, state, currentNodeId);
            } catch (Exception e) {
                logger.error("节点执行失败: {}", currentNodeId, e);
                state.setFailed(true);
                state.setErrorMessage(e.getMessage());
                break;
            }
        }

        return state;
    }

    private String findNextNode(WorkflowDefinition definition, WorkflowState state, String currentNodeId) {
        List<WorkflowEdge> edges = definition.getEdgesFrom(currentNodeId);

        for (WorkflowEdge edge : edges) {
            if (edge.evaluate(state)) {
                return edge.getToNodeId();
            }
        }

        return null;
    }
}
