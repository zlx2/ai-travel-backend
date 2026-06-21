package com.sora.aitravel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = ExternalIntegrationTestSupport.ENABLE_PROPERTY, matches = "true")
class DashScopeEmbeddingIntegrationTest extends ExternalIntegrationTestSupport {

    @Test
    void dashScopeCanCreateEmbeddingThroughOpenAiCompatibleApi() throws Exception {
        String baseUrl = withoutTrailingSlash(requiredEnv("DASHSCOPE_BASE_URL"));
        String endpoint =
                baseUrl.endsWith("/v1") ? baseUrl + "/embeddings" : baseUrl + "/v1/embeddings";
        ObjectMapper objectMapper = new ObjectMapper();
        String requestBody =
                objectMapper.writeValueAsString(
                        Map.of(
                                "model",
                                "text-embedding-v4",
                                "input",
                                List.of("AI 智行伴旅配置测试"),
                                "dimensions",
                                1024,
                                "encoding_format",
                                "float"));
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(30))
                        .header("Authorization", "Bearer " + requiredEnv("DASHSCOPE_API_KEY"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

        HttpResponse<String> response =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()
                        .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as("DashScope HTTP 状态码").isBetween(200, 299);
        JsonNode root = objectMapper.readTree(response.body());
        assertThat(root.path("data").isArray()).isTrue();
        assertThat(root.path("data").path(0).path("embedding").size()).isEqualTo(1024);
    }
}
