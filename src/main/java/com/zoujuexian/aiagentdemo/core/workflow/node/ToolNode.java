package com.zoujuexian.aiagentdemo.core.workflow.node;

import com.zoujuexian.aiagentdemo.core.workflow.WorkflowNode;
import com.zoujuexian.aiagentdemo.core.workflow.WorkflowState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

public class ToolNode implements WorkflowNode {

    private final String id;
    private final String toolName;
    private final String inputVariable;
    private final String outputVariable;
    private final List<ToolCallback> tools;

    public ToolNode(String id, String toolName, String inputVariable, 
                    String outputVariable, List<ToolCallback> tools) {
        this.id = id;
        this.toolName = toolName;
        this.inputVariable = inputVariable;
        this.outputVariable = outputVariable;
        this.tools = tools;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return "TOOL";
    }

    @Override
    public WorkflowState execute(WorkflowState state, ChatClient chatClient) {
        Object input = state.getVariable(inputVariable);
        String inputStr = input != null ? input.toString() : "";

        ToolCallback tool = tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到工具: " + toolName));

        String result = tool.call(inputStr);
        state.setVariable(outputVariable, result);
        return state;
    }
}
