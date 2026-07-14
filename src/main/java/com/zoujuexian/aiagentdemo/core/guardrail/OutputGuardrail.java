package com.zoujuexian.aiagentdemo.core.guardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OutputGuardrail {

    private static final Logger logger = LoggerFactory.getLogger(OutputGuardrail.class);

    public String sanitize(String output) {
        if (output == null) {
            return "";
        }

        String sanitized = output;

        sanitized = maskPii(sanitized);

        if (containsHarmfulContent(sanitized)) {
            logger.warn("输出内容审核未通过");
            return "[内容审核不通过] 输出包含违规内容";
        }

        return sanitized;
    }

    private String maskPii(String text) {
        text = text.replaceAll("[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]", "***身份证号***");
        text = text.replaceAll("[1-9]\\d{10,14}", "***银行卡号***");
        text = text.replaceAll("1[3-9]\\d{9}", "***手机号***");
        text = text.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "***邮箱***");
        return text;
    }

    private boolean containsHarmfulContent(String text) {
        String[] harmfulPatterns = {
                "攻击", "入侵", "破解", "窃取", "诈骗",
                "色情", "暴力", "恐怖", "反动"
        };
        String lowerText = text.toLowerCase();
        for (String pattern : harmfulPatterns) {
            if (lowerText.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
