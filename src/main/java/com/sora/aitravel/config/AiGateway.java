package com.sora.aitravel.config;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/** 项目级 AI 调用门面。业务节点仍负责构造自己的提示词。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiGateway {
    private final ChatModel chatModel;

    public String callText(String sceneName, String userPrompt) {
        return callText(sceneName, null, userPrompt);
    }

    public String callText(String sceneName, String systemPrompt, String userPrompt) {
        long start = System.currentTimeMillis();
        try {
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            String content =
                    hasText(systemPrompt)
                            ? chatClient
                                    .prompt()
                                    .system(systemPrompt)
                                    .user(userPrompt)
                                    .call()
                                    .content()
                            : chatClient.prompt().user(userPrompt).call().content();
            if (!hasText(content)) {
                throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "AI 返回为空");
            }
            log.info(
                    "{} 调用完成，耗时={}ms，promptLength={}，responseLength={}",
                    sceneName,
                    System.currentTimeMillis() - start,
                    length(systemPrompt) + length(userPrompt),
                    content.length());
            return content;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("{} 调用失败", sceneName, ex);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, sceneName + "服务调用失败");
        }
    }

    public String callJsonObject(String sceneName, String userPrompt) {
        return extractJsonObject(callText(sceneName, userPrompt));
    }

    public String callJsonObject(String sceneName, String systemPrompt, String userPrompt) {
        return extractJsonObject(callText(sceneName, systemPrompt, userPrompt));
    }

    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "AI 未返回合法 JSON");
        }
        return content.substring(start, end + 1);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
