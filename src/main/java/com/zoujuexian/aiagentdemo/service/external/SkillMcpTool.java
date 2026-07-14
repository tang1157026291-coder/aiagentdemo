package com.zoujuexian.aiagentdemo.service.external;

import com.zoujuexian.aiagentdemo.service.skill.SkillManager;
import com.zoujuexian.aiagentdemo.service.skill.SkillManager.SkillDefinition;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class SkillMcpTool {

    private static final Logger logger = LoggerFactory.getLogger(SkillMcpTool.class);

    @Resource
    private SkillManager skillManager;

    @Resource
    private ChatClient chatClient;

    @Tool(name = "execute_skill", description = "执行预定义的技能，如代码审查、文档生成、翻译等")
    public String executeSkill(
            @ToolParam(description = "技能名称，如：code_review、translate、summarize") String skillName,
            @ToolParam(description = "技能参数，JSON格式") String paramsJson
    ) {
        SkillDefinition skill = skillManager.getSkillDefinitions().stream()
                .filter(s -> s.name().equals(skillName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到技能: " + skillName));

        String prompt = renderSkillPrompt(skill, paramsJson);

        String response = chatClient.prompt()
                .user(prompt)
                .call().content();

        logger.debug("技能执行完成: {}, 结果长度: {}", skillName, 
                response != null ? response.length() : 0);
        return response != null ? response : "";
    }

    private String renderSkillPrompt(SkillDefinition skill, String paramsJson) {
        String template = skill.promptTemplate();
        if (paramsJson != null && !paramsJson.isBlank()) {
            template = template.replace("{{input}}", paramsJson);
        }
        return template;
    }
}
