package com.zoujuexian.aiagentdemo.core.react;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zoujuexian.aiagentdemo.api.exception.AgentException;
import com.zoujuexian.aiagentdemo.api.exception.ErrorCode;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReActExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ReActExecutor.class);

    private static final int MAX_ITERATIONS = 10;
    private static final int RETRY_ATTEMPTS = 3;

    @Resource
    private ChatClient chatClient;

    @Resource
    private ReActPromptTemplate promptTemplate;

    public ReActResult execute(String userInput, List<ToolCallback> tools) {
        return execute(userInput, tools, MAX_ITERATIONS);
    }

    public ReActResult execute(String userInput, List<ToolCallback> tools, int maxIterations) {
        long startTime = System.currentTimeMillis();
        ReActState state = new ReActState();
        state.setCurrentInput(userInput);

        try {
            for (int iteration = 1; iteration <= maxIterations; iteration++) {
                logger.debug("ReAct 迭代 {} 开始", iteration);

                String prompt = buildPrompt(state);
                String response = chatClient.prompt()
                        .system(promptTemplate.buildSystemPrompt(tools))
                        .user(prompt)
                        .call().content();

                ReActDecision decision = parseDecision(response);

                ReActState.Step step = new ReActState.Step(
                        iteration, decision.thought(), decision.action(),
                        decision.actionInput(), null, System.currentTimeMillis()
                );

                if ("finish".equals(decision.action()) || decision.isFinal()) {
                    state.setFinished(true);
                    state.setFinalAnswer(decision.actionInput());
                    step = new ReActState.Step(
                            iteration, decision.thought(), "finish",
                            decision.actionInput(), "直接回答", System.currentTimeMillis()
                    );
                    state.addStep(step);
                    logger.debug("ReAct 完成，最终答案: {}", decision.actionInput());
                    break;
                }

                String observation = executeToolWithRetry(tools, decision.action(), decision.actionInput());
                step = new ReActState.Step(
                        iteration, decision.thought(), decision.action(),
                        decision.actionInput(), observation, System.currentTimeMillis()
                );
                state.addStep(step);
                state.setCurrentInput(observation);

                logger.debug("ReAct 迭代 {} 完成，观察结果: {}", iteration, observation);
            }

            long totalTime = System.currentTimeMillis() - startTime;
            return ReActResult.builder()
                    .finalAnswer(state.getFinalAnswer())
                    .steps(state.getSteps())
                    .success(true)
                    .iterations(state.getStepCount())
                    .totalTimeMs(totalTime)
                    .build();

        } catch (Exception e) {
            logger.error("ReAct 执行失败", e);
            return ReActResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .steps(state.getSteps())
                    .iterations(state.getStepCount())
                    .totalTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private String buildPrompt(ReActState state) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("当前问题: ").append(state.getCurrentInput()).append("\n\n");
        promptBuilder.append("历史思考步骤:\n");

        for (ReActState.Step step : state.getSteps()) {
            promptBuilder.append(step.stepNumber()).append(". ")
                    .append("Thought: ").append(step.thought()).append("\n");
            promptBuilder.append("   Action: ").append(step.action()).append("\n");
            promptBuilder.append("   Observation: ").append(step.observation()).append("\n\n");
        }

        promptBuilder.append("请根据历史步骤继续思考并输出下一步操作。");
        return promptBuilder.toString();
    }

    private ReActDecision parseDecision(String response) {
        try {
            JSONObject json = JSON.parseObject(response);
            return new ReActDecision(
                    json.getString("thought"),
                    json.getString("action"),
                    json.getString("actionInput"),
                    json.getBooleanValue("isFinal")
            );
        } catch (Exception e) {
            logger.warn("解析 ReAct 响应失败，尝试纯文本解析: {}", response);
            return new ReActDecision("解析失败，直接回答", "finish", response, true);
        }
    }

    private String executeToolWithRetry(List<ToolCallback> tools, String toolName, String input) {
        ToolCallback tool = tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AgentException(ErrorCode.TOOL_ERROR, "未找到工具: " + toolName));

        for (int attempt = 1; attempt <= RETRY_ATTEMPTS; attempt++) {
            try {
                logger.debug("调用工具 {} (第 {} 次尝试)", toolName, attempt);
                return tool.call(input);
            } catch (Exception e) {
                logger.warn("工具调用失败 (第 {} 次): {}", attempt, e.getMessage());
                if (attempt == RETRY_ATTEMPTS) {
                    return "[工具调用失败] " + e.getMessage();
                }
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return "[工具调用失败] 达到最大重试次数";
    }

    private record ReActDecision(String thought, String action, String actionInput, boolean isFinal) {}
}
