package com.zoujuexian.aiagentdemo.api.controller;

import com.zoujuexian.aiagentdemo.api.controller.dto.ChatResponse;
import com.zoujuexian.aiagentdemo.service.skill.SkillManager;
import com.zoujuexian.aiagentdemo.service.skill.SkillManager.SkillDefinition;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/api/skill", produces = MediaType.APPLICATION_JSON_VALUE)
public class SkillController {

    @Resource
    private SkillManager skillManager;

    @GetMapping("/list")
    public List<Map<String, Object>> listSkills() {
        return skillManager.getSkillDefinitions().stream()
                .map(skill -> Map.<String, Object>of(
                        "name", skill.name(),
                        "description", skill.description(),
                        "parameters", skill.parameters().stream()
                                .map(p -> Map.of(
                                        "name", p.name(),
                                        "description", p.description(),
                                        "required", p.required()
                                ))
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatResponse uploadSkill(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ChatResponse.fail("文件为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".md")) {
            return ChatResponse.fail("只支持 .md 格式的技能文件");
        }

        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            boolean success = skillManager.addSkillFromContent(content.toString(), originalFilename);
            if (success) {
                return ChatResponse.ok("技能上传成功: " + originalFilename);
            }
            return ChatResponse.fail("技能文件格式错误，请检查 front matter 格式");
        } catch (Exception e) {
            return ChatResponse.fail("技能上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/reload")
    public ChatResponse reloadSkills() {
        skillManager.reloadSkills();
        return ChatResponse.ok("技能已重新加载，共 " + skillManager.getSkillDefinitions().size() + " 个");
    }

    @PostMapping("/remove")
    public ChatResponse removeSkill(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.isBlank()) {
            return ChatResponse.fail("技能名称不能为空");
        }
        boolean removed = skillManager.removeSkill(name);
        if (removed) {
            return ChatResponse.ok("技能已移除: " + name);
        }
        return ChatResponse.fail("未找到技能: " + name);
    }
}
