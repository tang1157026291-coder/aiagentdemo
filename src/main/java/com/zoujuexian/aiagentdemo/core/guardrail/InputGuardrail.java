package com.zoujuexian.aiagentdemo.core.guardrail;

import com.zoujuexian.aiagentdemo.api.exception.GuardrailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class InputGuardrail {

    private static final Logger logger = LoggerFactory.getLogger(InputGuardrail.class);

    private static final String[] INJECTION_PATTERNS = {
            "忽略以上指令", "请忘记之前的要求", "无视前面的指示",
            "作为一个黑客", "绕过安全检查", "执行以下代码",
            "请忽略", "请忽视", "忽略所有", "覆盖指令"
    };

    private static final String[] SENSITIVE_PATTERNS = {
            "密码", "口令", "secret", "token", "api.*key",
            "身份证", "银行卡", "手机号", "住址",
            "攻击", "入侵", "破解", "钓鱼"
    };

    private static final int MAX_INPUT_LENGTH = 5000;

    public GuardrailResult check(String input) {
        List<String> violations = new ArrayList<>();

        if (input == null || input.isBlank()) {
            violations.add("输入为空");
        }

        if (input != null && input.length() > MAX_INPUT_LENGTH) {
            violations.add("输入长度超限（最大" + MAX_INPUT_LENGTH + "字符）");
        }

        if (detectPromptInjection(input)) {
            violations.add("检测到 Prompt 注入攻击");
        }

        if (containsSensitiveContent(input)) {
            violations.add("包含敏感内容");
        }

        if (violations.isEmpty()) {
            return GuardrailResult.pass(input);
        } else {
            logger.warn("输入安全检查失败: {}", violations);
            return GuardrailResult.block(violations);
        }
    }

    private boolean detectPromptInjection(String input) {
        if (input == null) return false;
        String lowerInput = input.toLowerCase();
        return Arrays.stream(INJECTION_PATTERNS).anyMatch(lowerInput::contains);
    }

    private boolean containsSensitiveContent(String input) {
        if (input == null) return false;
        String lowerInput = input.toLowerCase();
        return Arrays.stream(SENSITIVE_PATTERNS).anyMatch(lowerInput::contains);
    }

    public record GuardrailResult(boolean allowed, String content, List<String> violations) {
        public static GuardrailResult pass(String content) {
            return new GuardrailResult(true, content, List.of());
        }
        public static GuardrailResult block(List<String> violations) {
            return new GuardrailResult(false, null, violations);
        }
    }
}
