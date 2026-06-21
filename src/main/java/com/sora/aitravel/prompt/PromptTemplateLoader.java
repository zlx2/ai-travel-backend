package com.sora.aitravel.prompt;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptTemplateLoader {
    /** Prompt 文件只读，进程内缓存可避免每次模型调用都访问 classpath。 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String fileName, Map<String, ?> variables) {
        String template = cache.computeIfAbsent(fileName, this::readTemplate);
        String result = template;
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            // 模板占位符统一使用 {{name}}，未提供的变量保持原样，便于排查配置错误。
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String readTemplate(String fileName) {
        try {
            return new ClassPathResource("prompts/" + fileName)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Prompt 模板读取失败: " + fileName);
        }
    }
}
