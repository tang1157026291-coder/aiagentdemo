package com.zoujuexian.aiagentdemo.core.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TaskDecomposer {

    private static final Logger logger = LoggerFactory.getLogger(TaskDecomposer.class);

    private static final String DECOMPOSE_PROMPT = """
            你是一个任务分解专家。请将以下复杂任务分解为若干个独立的子任务。
            
            任务：{{task}}
            
            要求：
            1. 子任务数量：2-5个
            2. 每个子任务必须是独立的，可以由不同的 Agent 并行执行
            3. 子任务之间应该有明确的依赖关系（如果有的话）
            4. 输出格式：JSON数组，每个元素包含 id、name、description、input
            
            示例输出：
            [
              {"id": "t1", "name": "子任务1", "description": "描述", "input": "输入"},
              {"id": "t2", "name": "子任务2", "description": "描述", "input": "输入"}
            ]
            """;

    @Resource
    private ChatClient chatClient;

    public List<AgentTask> decompose(String task) {
        String prompt = DECOMPOSE_PROMPT.replace("{{task}}", task);

        String response = chatClient.prompt()
                .user(prompt)
                .call().content();

        return parseTasks(response);
    }

    private List<AgentTask> parseTasks(String response) {
        List<AgentTask> tasks = new ArrayList<>();
        try {
            JSONArray jsonArray = JSON.parseArray(response);
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                AgentTask task = new AgentTask(
                        json.getString("id"),
                        json.getString("name"),
                        json.getString("description"),
                        json.getString("input")
                );
                tasks.add(task);
            }
        } catch (Exception e) {
            logger.warn("解析任务失败，回退到单任务", e);
            AgentTask task = new AgentTask(
                    UUID.randomUUID().toString(),
                    "主任务",
                    "原始任务",
                    response
            );
            tasks.add(task);
        }
        return tasks;
    }
}
