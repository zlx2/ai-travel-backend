package com.sora.aitravel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

@EnabledIfSystemProperty(named = ExternalIntegrationTestSupport.ENABLE_PROPERTY, matches = "true")
class DeepSeekIntegrationTest extends ExternalIntegrationTestSupport {

    @Test
    void officialDeepSeekModelCanCompleteMinimalPrompt() {
        DeepSeekApi api =
                DeepSeekApi.builder()
                        .baseUrl(withoutTrailingSlash(requiredEnv("DEEPSEEK_BASE_URL")))
                        .apiKey(requiredEnv("DEEPSEEK_API_KEY"))
                        .build();
        DeepSeekChatOptions options =
                DeepSeekChatOptions.builder()
                        .model(requiredEnv("DEEPSEEK_MODEL"))
                        .temperature(0.0)
                        // 推理模型可能先消耗一部分 token 进行思考，额度过小会导致最终文本为空。
                        .maxTokens(128)
                        .build();
        DeepSeekChatModel model =
                DeepSeekChatModel.builder().deepSeekApi(api).defaultOptions(options).build();

        ChatResponse response = model.call(new Prompt("只回复 OK 两个字母，不要添加其他内容。"));

        assertThat(response).isNotNull();
        assertThat(response.getResult()).isNotNull();
        assertThat(response.getResult().getOutput().getText()).isNotBlank();
    }
}
