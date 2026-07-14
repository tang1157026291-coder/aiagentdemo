package com.zoujuexian.aiagentdemo.core.workflow.node;

import com.zoujuexian.aiagentdemo.core.workflow.WorkflowNode;
import com.zoujuexian.aiagentdemo.core.workflow.WorkflowState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.Map;

public class LlmNode implements WorkflowNode {

    private final String id;
    private final String promptTemplate;
    private final String outputVariable;
    private final double temperature;

    public LlmNode(String id, String promptTemplate, String outputVariable) {
        this(id, promptTemplate, outputVariable, 0.7);
    }

    public LlmNode(String id, String promptTemplate, String outputVariable, double temperature) {
        this.id = id;
        this.promptTemplate = promptTemplate;
        this.outputVariable = outputVariable;
        this.temperature = temperature;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getType() {
        return "LLM";
    }

    @Override
    public WorkflowState execute(WorkflowState state, ChatClient chatClient) {
        String prompt = renderTemplate(promptTemplate, state.getVariables());

        String response = chatClient.prompt()
                .user(prompt)
                .options(OpenAiChatOptions.builder()
                        .temperature(temperature)
                        .maxTokens(2048)
                        .build())
                .call().content();

        state.setVariable(outputVariable, response != null ? response : "");
        return state;
    }

    private String renderTemplate(String template, Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
