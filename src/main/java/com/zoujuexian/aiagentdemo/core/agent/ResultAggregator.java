package com.zoujuexian.aiagentdemo.core.agent;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResultAggregator {

    private static final Logger logger = LoggerFactory.getLogger(ResultAggregator.class);

    private static final String AGGREGATE_PROMPT = """
            你是一个结果汇总专家。请将以下多个子任务的结果汇总为一个完整的回答。
            
            原始问题：{{originalQuestion}}
            
            子任务结果：
            {{results}}
            
            要求：
            1. 整合所有子任务的结果，去除重复信息
            2. 按照逻辑顺序组织答案
            3. 如果子任务之间有冲突，以最新或最可靠的结果为准
            4. 输出格式：自然语言回答，不需要JSON格式
            """;

    @Resource
    private ChatClient chatClient;

    public String aggregate(String originalQuestion, List<AgentResult> results) {
        StringBuilder resultsStr = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            AgentResult result = results.get(i);
            resultsStr.append(i + 1).append(". [").append(result.getAgentName()).append("] ")
                    .append(result.getContent()).append("\n");
        }

        String prompt = AGGREGATE_PROMPT
                .replace("{{originalQuestion}}", originalQuestion)
                .replace("{{results}}", resultsStr.toString());

        String response = chatClient.prompt()
                .user(prompt)
                .call().content();

        return response != null ? response : "无法汇总结果";
    }
}
