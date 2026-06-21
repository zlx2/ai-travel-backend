package com.sora.aitravel.integration;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = ExternalIntegrationTestSupport.ENABLE_PROPERTY, matches = "true")
class EnvironmentConfigurationIntegrationTest extends ExternalIntegrationTestSupport {

    private static final List<String> REQUIRED_VARIABLES =
            List.of(
                    "MYSQL_HOST",
                    "MYSQL_PORT",
                    "MYSQL_DATABASE",
                    "MYSQL_USERNAME",
                    "MYSQL_PASSWORD",
                    "REDIS_HOST",
                    "REDIS_PORT",
                    "REDIS_USERNAME",
                    "REDIS_PASSWORD",
                    "RABBITMQ_HOST",
                    "RABBITMQ_PORT",
                    "RABBITMQ_USERNAME",
                    "RABBITMQ_PASSWORD",
                    "MAIL_HOST",
                    "MAIL_PORT",
                    "MAIL_USERNAME",
                    "MAIL_PASSWORD",
                    "EMAIL_MOCK_CODE",
                    "COS_SECRET_ID",
                    "COS_SECRET_KEY",
                    "COS_BUCKET",
                    "COS_REGION",
                    "COS_DOMAIN",
                    "DEEPSEEK_API_KEY",
                    "DEEPSEEK_BASE_URL",
                    "DEEPSEEK_MODEL",
                    "DASHSCOPE_BASE_URL",
                    "DASHSCOPE_API_KEY",
                    "REDIS_STACK_HOST",
                    "REDIS_STACK_PORT",
                    "REDIS_STACK_USERNAME",
                    "REDIS_STACK_PASSWORD",
                    "AI_MOCK_ENABLED");

    @Test
    void requiredVariablesAreVisibleAndWellFormed() {
        REQUIRED_VARIABLES.forEach(ExternalIntegrationTestSupport::requiredEnv);

        List.of("MYSQL_PORT", "REDIS_PORT", "RABBITMQ_PORT", "MAIL_PORT", "REDIS_STACK_PORT")
                .forEach(ExternalIntegrationTestSupport::requiredPort);

        List.of("COS_DOMAIN", "DEEPSEEK_BASE_URL", "DASHSCOPE_BASE_URL")
                .forEach(
                        name ->
                                assertThatCode(() -> URI.create(requiredEnv(name)).toURL())
                                        .as("%s 必须是合法 URL", name)
                                        .doesNotThrowAnyException());
    }
}
