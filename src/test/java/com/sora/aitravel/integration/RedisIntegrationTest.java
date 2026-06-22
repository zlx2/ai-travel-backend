package com.sora.aitravel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@EnabledIfSystemProperty(named = ExternalIntegrationTestSupport.ENABLE_PROPERTY, matches = "true")
class RedisIntegrationTest extends ExternalIntegrationTestSupport {

    @Test
    void redisCanWriteReadAndDeleteTemporaryValue() {
        LettuceConnectionFactory factory =
                createFactory(
                        "REDIS_HOST",
                        "REDIS_PORT",
                        "REDIS_USERNAME",
                        "REDIS_PASSWORD",
                        Integer.parseInt(optionalEnv("REDIS_DATABASE", "0")));
        byte[] key = ("plango:test:" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        byte[] value = "ok".getBytes(StandardCharsets.UTF_8);

        try (RedisConnection connection = factory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
            assertThat(connection.stringCommands().set(key, value)).isTrue();
            assertThat(connection.stringCommands().get(key)).isEqualTo(value);
            assertThat(connection.keyCommands().del(key)).isEqualTo(1L);
        } finally {
            factory.destroy();
        }
    }

    @Test
    void redisStackExposesRediSearchCommand() {
        LettuceConnectionFactory factory =
                createFactory(
                        "REDIS_STACK_HOST",
                        "REDIS_STACK_PORT",
                        "REDIS_STACK_USERNAME",
                        "REDIS_STACK_PASSWORD",
                        0);

        try (RedisConnection connection = factory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
            assertThat(connection.execute("FT._LIST")).isNotNull();
        } finally {
            factory.destroy();
        }
    }

    private LettuceConnectionFactory createFactory(
            String hostVariable,
            String portVariable,
            String usernameVariable,
            String passwordVariable,
            int database) {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(
                        requiredEnv(hostVariable), requiredPort(portVariable));
        configuration.setDatabase(database);
        configuration.setUsername(requiredEnv(usernameVariable));
        configuration.setPassword(RedisPassword.of(requiredEnv(passwordVariable)));

        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }
}
