package com.sora.aitravel.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sora.aitravel.common.exception.BusinessException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiGatewayTest {

    @Mock private ChatModel chatModel;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private AiGateway aiGateway;

    @BeforeEach
    void setUp() {
        aiGateway = new AiGateway(chatModel, redisTemplate);
        ReflectionTestUtils.setField(aiGateway, "deepseekModel", "deepseek-v4-flash");
        ReflectionTestUtils.setField(aiGateway, "deepseekDirectEnabled", false);
        ReflectionTestUtils.setField(aiGateway, "deepseekDisableThinking", true);
        ReflectionTestUtils.setField(aiGateway, "deepseekTimeout", Duration.ofSeconds(45));
        ReflectionTestUtils.setField(aiGateway, "responseCacheEnabled", true);
        ReflectionTestUtils.setField(aiGateway, "responseCacheTtl", Duration.ofHours(12));
        ReflectionTestUtils.setField(aiGateway, "cacheKeyPrefix", "plango:test");
    }

    @Test
    void defaultPathUsesOfficialSpringAiChatModelAndWritesCache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(chatModel.call(any(Prompt.class))).thenReturn(response("{\"ok\":true}"));

        String content = aiGateway.callText("AI 景点推荐", "成都热门景点");

        assertThat(content).isEqualTo("{\"ok\":true}");
        verify(chatModel).call(any(Prompt.class));
        verify(valueOperations)
                .set(
                        org.mockito.ArgumentMatchers.contains(
                                "plango:test:ai-response:v2:AI_景点推荐:deepseek-v4-flash:no-think:"),
                        eq("{\"ok\":true}"),
                        eq(Duration.ofHours(12)));
    }

    @Test
    void cacheHitReturnsCachedResponseWithoutCallingModel() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("{\"cached\":true}");

        String content = aiGateway.callText("AI 景点推荐", "成都热门景点");

        assertThat(content).isEqualTo("{\"cached\":true}");
        verify(chatModel, never()).call(any(Prompt.class));
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void dynamicEditSceneIsNeverCached() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("{\"first\":true}"))
                .thenReturn(response("{\"second\":true}"));

        String first = aiGateway.callText("AI 行程动态修改", "不想去这里");
        String second = aiGateway.callText("AI 行程动态修改", "不想去这里");

        assertThat(first).isEqualTo("{\"first\":true}");
        assertThat(second).isEqualTo("{\"second\":true}");
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void dayPlanGenerationSceneIsNeverCached() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(response("{\"first\":true}"))
                .thenReturn(response("{\"second\":true}"));

        String first = aiGateway.callText("AI 行程生成", "给我一天行程");
        String second = aiGateway.callText("AI 行程生成", "给我一天行程");

        assertThat(first).isEqualTo("{\"first\":true}");
        assertThat(second).isEqualTo("{\"second\":true}");
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void directDeepseekModeIsOptInAndDoesNotFallbackSilentlyToSpringAi() {
        ReflectionTestUtils.setField(aiGateway, "deepseekDirectEnabled", true);
        ReflectionTestUtils.setField(aiGateway, "responseCacheEnabled", false);
        ReflectionTestUtils.setField(aiGateway, "deepseekApiKey", "test-key");
        ReflectionTestUtils.setField(aiGateway, "deepseekBaseUrl", "http://127.0.0.1:1");
        ReflectionTestUtils.setField(aiGateway, "deepseekTimeout", Duration.ofMillis(200));

        assertThatThrownBy(() -> aiGateway.callText("AI 行程生成", "给我一天行程"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务调用失败");
        verify(chatModel, never()).call(any(Prompt.class));
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
