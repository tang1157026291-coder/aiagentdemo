package com.zoujuexian.aiagentdemo.core.react;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReActPromptTemplate {

    private static final String REACT_SYSTEM_PROMPT = """
            你是一个智能推理助手，遵循 ReAct 框架进行思考和行动。
            
            思考步骤：
            1. Thought：分析当前问题，判断是否需要调用工具
            2. Action：如果需要，选择合适的工具并调用
            3. Observation：获取工具执行结果
            4. Repeat：根据结果决定继续思考或直接回答
            
            可用工具：{{tools}}
            
            输出格式要求：
            请严格按照以下 JSON 格式输出：
            {
              "thought": "你的思考过程",
              "action": "工具名称或 'finish' 如果可以直接回答",
              "actionInput": "工具输入参数（JSON格式）或最终答案",
              "isFinal": false
            }
            
            如果 action 为 'finish'，isFinal 必须为 true，actionInput 为最终答案。
            """;

    public String buildSystemPrompt(List<ToolCallback> tools) {
        String toolDescriptions = tools.stream()
                .map(t -> {
                    var def = t.getToolDefinition();
                    return "- " + def.name() + ": " + def.description();
                })
                .collect(Collectors.joining("\n"));
        return REACT_SYSTEM_PROMPT.replace("{{tools}}", toolDescriptions);
    }
}
