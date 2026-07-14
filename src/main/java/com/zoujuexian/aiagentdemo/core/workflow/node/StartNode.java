package com.zoujuexian.aiagentdemo.core.workflow.node;

import com.zoujuexian.aiagentdemo.core.workflow.WorkflowNode;
import com.zoujuexian.aiagentdemo.core.workflow.WorkflowState;
import org.springframework.ai.chat.client.ChatClient;

public class StartNode implements WorkflowNode {

    private final String id;

    public StartNode(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return "START";
    }

    @Override
    public WorkflowState execute(WorkflowState state, ChatClient chatClient) {
        return state;
    }
}
