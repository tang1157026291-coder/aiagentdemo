package com.zoujuexian.aiagentdemo.core.action;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DangerousActionDetector {

    private static final List<String> DANGEROUS_KEYWORDS = Arrays.asList(
            "删除", "delete", "remove", "drop", "clear", "purge",
            "禁令", "禁止", "禁用", "ban", "block", "disable",
            "修改密码", "reset password", "change password",
            "转账", "支付", "付款", "pay", "transfer",
            "清空", "重置", "reset", "初始化", "init",
            "卸载", "uninstall", "注销", "unregister",
            "关闭", "关闭服务", "shutdown", "stop",
            "发布", "发布到生产", "deploy", "publish"
    );

    private static final List<String> DANGEROUS_TOOL_PATTERNS = Arrays.asList(
            ".*delete.*", ".*remove.*", ".*drop.*",
            ".*update.*pass.*", ".*reset.*pass.*",
            ".*pay.*", ".*transfer.*",
            ".*ban.*", ".*block.*", ".*disable.*"
    );

    public boolean isDangerous(String actionDescription) {
        if (actionDescription == null || actionDescription.isBlank()) {
            return false;
        }
        String lower = actionDescription.toLowerCase();
        return DANGEROUS_KEYWORDS.stream().anyMatch(k -> lower.contains(k.toLowerCase()));
    }

    public boolean isDangerousTool(String toolName, String toolInput) {
        if (toolName == null) return false;
        String lowerName = toolName.toLowerCase();
        String lowerInput = toolInput != null ? toolInput.toLowerCase() : "";
        
        if (isDangerous(toolName) || isDangerous(toolInput)) {
            return true;
        }
        
        return DANGEROUS_TOOL_PATTERNS.stream()
                .anyMatch(p -> lowerName.matches(p) || lowerInput.matches(".*" + p + ".*"));
    }

    public String generateWarning(String actionDescription) {
        return "⚠️ 检测到高风险操作: \"" + actionDescription + "\"\n"
                + "此操作可能会造成不可逆的影响，请确认是否继续？";
    }
}
