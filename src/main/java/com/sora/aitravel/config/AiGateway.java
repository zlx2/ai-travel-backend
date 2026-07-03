package com.sora.aitravel.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

/** 项目级 AI 调用门面。业务节点仍负责构造自己的提示词。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiGateway {
    private final ChatModel chatModel;
    private final StringRedisTemplate redisTemplate;

    @Value("${spring.ai.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${spring.ai.deepseek.chat.options.model:deepseek-v4-flash}")
    private String deepseekModel;

    @Value("${app.ai.deepseek-direct-enabled:true}")
    private boolean deepseekDirectEnabled;

    @Value("${app.ai.deepseek-disable-thinking:true}")
    private boolean deepseekDisableThinking;

    @Value("${app.ai.deepseek-timeout:45s}")
    private Duration deepseekTimeout;

    @Value("${app.ai.response-cache-enabled:true}")
    private boolean responseCacheEnabled;

    @Value("${app.ai.response-cache-ttl:12h}")
    private Duration responseCacheTtl;

    @Value("${app.cache.key-prefix:plango:dev}")
    private String cacheKeyPrefix;

    public String callText(String sceneName, String userPrompt) {
        return callText(sceneName, null, userPrompt);
    }

    public String callText(String sceneName, String systemPrompt, String userPrompt) {
        long start = System.currentTimeMillis();
        String promptHash = promptHash(systemPrompt, userPrompt);
        String cacheKey = cacheKey(sceneName, promptHash);
        String cached = readCache(sceneName, cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            String content = deepseekDirectEnabled
                    ? callDeepseekDirect(systemPrompt, userPrompt)
                    : callSpringAi(systemPrompt, userPrompt);
            if (!hasText(content)) {
                throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "AI 返回为空");
            }
            log.info(
                    "{} 调用完成，provider={}, model={}, thinkingDisabled={}, 耗时={}ms，promptLength={}，responseLength={}, promptHash={}, promptPreview={}",
                    sceneName,
                    deepseekDirectEnabled ? "deepseek-direct" : "spring-ai",
                    deepseekModel,
                    deepseekDisableThinking,
                    System.currentTimeMillis() - start,
                    length(systemPrompt) + length(userPrompt),
                    content.length(),
                    promptHash,
                    promptPreview(systemPrompt, userPrompt));
            writeCache(sceneName, cacheKey, content);
            return content;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("{} 调用失败，reason={}", sceneName, ex.getMessage());
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
        content = stripThinkTag(content);
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

    private String callSpringAi(String systemPrompt, String userPrompt) {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        return hasText(systemPrompt)
                ? chatClient.prompt().system(systemPrompt).user(userPrompt).call().content()
                : chatClient.prompt().user(userPrompt).call().content();
    }

    private String callDeepseekDirect(String systemPrompt, String userPrompt) {
        String apiKey = StrUtil.blankToDefault(deepseekApiKey, System.getenv("DEEPSEEK_API_KEY"));
        if (!hasText(apiKey)) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "DeepSeek API Key 未配置");
        }
        JSONArray messages = new JSONArray();
        if (hasText(systemPrompt)) {
            messages.add(JSONUtil.createObj().set("role", "system").set("content", systemPrompt));
        }
        messages.add(JSONUtil.createObj().set("role", "user").set("content", userPrompt));
        JSONObject body =
                JSONUtil.createObj()
                        .set("model", deepseekModel)
                        .set("messages", messages)
                        .set("temperature", 0.15)
                        .set("max_tokens", 900)
                        .set("response_format", JSONUtil.createObj().set("type", "json_object"));
        if (deepseekDisableThinking) {
            body.set("thinking", JSONUtil.createObj().set("type", "disabled"));
        }
        String response =
                HttpRequest.post(baseUrl() + "/chat/completions")
                        .bearerAuth(apiKey)
                        .contentType("application/json")
                        .body(body.toString())
                        .timeout((int) safeTimeout().toMillis())
                        .execute()
                        .body();
        JSONObject root = JSONUtil.parseObj(response);
        if (root.containsKey("error")) {
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_ERROR, "DeepSeek 返回错误：" + root.getJSONObject("error"));
        }
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_FORMAT_ERROR, "DeepSeek 未返回 choices");
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        return message == null ? null : stripThinkTag(message.getStr("content"));
    }

    private String baseUrl() {
        return StrUtil.blankToDefault(deepseekBaseUrl, "https://api.deepseek.com")
                .replaceAll("/+$", "");
    }

    private Duration safeTimeout() {
        return deepseekTimeout == null || deepseekTimeout.isNegative() || deepseekTimeout.isZero()
                ? Duration.ofSeconds(45)
                : deepseekTimeout;
    }

    private String stripThinkTag(String content) {
        if (content == null) {
            return null;
        }
        return content.replaceFirst("^\\s*</think>\\s*", "").trim();
    }

    private String promptHash(String systemPrompt, String userPrompt) {
        String raw = (systemPrompt == null ? "" : systemPrompt) + "\n---USER---\n" + userPrompt;
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String promptPreview(String systemPrompt, String userPrompt) {
        String raw =
                (hasText(systemPrompt) ? "[system]" + systemPrompt + " " : "")
                        + "[user]"
                        + (userPrompt == null ? "" : userPrompt);
        String normalized = raw.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 180) + "...";
    }

    private String cacheKey(String sceneName, String promptHash) {
        return cacheKeyPrefix
                + ":ai-response:v2:"
                + normalizeScene(sceneName)
                + ":"
                + deepseekModel
                + ":"
                + (deepseekDisableThinking ? "no-think" : "think")
                + ":"
                + promptHash;
    }

    private String readCache(String sceneName, String cacheKey) {
        if (!cacheableScene(sceneName)) {
            return null;
        }
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("{} AI 响应缓存命中，key={}", sceneName, cacheKey);
            }
            return cached;
        } catch (RuntimeException exception) {
            log.warn("{} AI 响应缓存读取失败，降级直调，reason={}", sceneName, exception.getMessage());
            return null;
        }
    }

    private void writeCache(String sceneName, String cacheKey, String content) {
        if (!cacheableScene(sceneName)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, content, safeCacheTtl());
        } catch (RuntimeException exception) {
            log.warn("{} AI 响应缓存写入失败，reason={}", sceneName, exception.getMessage());
        }
    }

    private boolean cacheableScene(String sceneName) {
        return responseCacheEnabled
                && List.of("AI 路线骨架", "AI 景点推荐").contains(sceneName);
    }

    private Duration safeCacheTtl() {
        return responseCacheTtl == null || responseCacheTtl.isNegative() || responseCacheTtl.isZero()
                ? Duration.ofHours(12)
                : responseCacheTtl;
    }

    private String normalizeScene(String sceneName) {
        return sceneName == null ? "unknown" : sceneName.replaceAll("[^\\p{L}\\p{N}_-]", "_");
    }
}
