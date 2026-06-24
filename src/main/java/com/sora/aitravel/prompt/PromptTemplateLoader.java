package com.sora.aitravel.prompt;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 提示词（Prompt）模板加载器。
 *
 * <p>负责从 classpath 的 prompts/ 目录下读取提示词模板文件，并进行变量替换。 模板采用 {{variableName}} 占位符语法，适用于所有 AI
 * 工作流（分析、生成、聊天） 中的 Prompt 构建场景。
 *
 * <p>设计特点：
 *
 * <ul>
 *   <li>进程内缓存：使用 {@link ConcurrentHashMap} 缓存已读取的模板文件， 避免每次模型调用都访问 classpath，提升性能。
 *   <li>只读模板：模板文件在运行时不会被修改。
 *   <li>未提供变量的占位符保持原样，便于排查配置错误。
 * </ul>
 *
 * <p>在整个项目中的位置：被各工作流的 Prompt 构建节点（如 {@link com.sora.aitravel.workflow.chat.ChatPromptBuildNode}）
 * 调用，用于渲染发送给 AI 模型的完整提示词。
 *
 * <p>输入：fileName（模板文件名，classpath:prompts/ 下的相对路径）、 variables（变量名-值映射，用于替换模板中的 {{变量名}} 占位符）。
 * 输出：替换变量后的提示词字符串。
 */
@Component
public class PromptTemplateLoader {

    /** Prompt 文件只读，进程内缓存可避免每次模型调用都访问 classpath。 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 加载并渲染提示词模板。
     *
     * <p>从缓存或 classpath 读取指定模板文件，然后将提供的变量值替换到模板占位符中。 模板占位符统一使用 {{name}} 格式，未提供的变量保持原样，便于排查配置错误。
     *
     * @param fileName 模板文件名（相对于 classpath:prompts/ 目录），例如 "analyze.txt"、"generate.txt"
     * @param variables 变量映射表，键为变量名（不含 {{}}），值为要替换的内容
     * @return 变量替换完成后的提示词字符串
     * @throws BusinessException 当模板文件读取失败时抛出，错误码为 AI_SERVICE_ERROR
     */
    public String load(String fileName, Map<String, ?> variables) {
        String template = cache.computeIfAbsent(fileName, this::readTemplate);
        String result = template;
        for (Map.Entry<String, ?> entry : variables.entrySet()) {
            // 模板占位符统一使用 {{name}}，未提供的变量保持原样，便于排查配置错误。
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    /**
     * 从 classpath 读取模板文件内容。
     *
     * <p>通过 {@link ClassPathResource} 从 classpath:prompts/{fileName} 路径读取文件， 并使用 UTF-8 编码解析为字符串。
     *
     * @param fileName 模板文件名（相对于 classpath:prompts/ 目录）
     * @return 模板文件的完整文本内容
     * @throws BusinessException 如果文件不存在或读取失败
     */
    private String readTemplate(String fileName) {
        try {
            return new ClassPathResource("prompts/" + fileName)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "Prompt 模板读取失败: " + fileName);
        }
    }
}
