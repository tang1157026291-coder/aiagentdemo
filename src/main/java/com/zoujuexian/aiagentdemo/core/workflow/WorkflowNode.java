package com.zoujuexian.aiagentdemo.core.workflow;

import org.springframework.ai.chat.client.ChatClient;

public interface WorkflowNode {

    String getId();
    String getType();
    WorkflowState execute(WorkflowState state, ChatClient chatClient);
    default boolean isTerminal() { return false; }
}
