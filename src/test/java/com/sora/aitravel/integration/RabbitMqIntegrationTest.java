package com.sora.aitravel.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = ExternalIntegrationTestSupport.ENABLE_PROPERTY, matches = "true")
class RabbitMqIntegrationTest extends ExternalIntegrationTestSupport {

    @Test
    void rabbitMqCanPublishAndConsumeTemporaryMessage() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(requiredEnv("RABBITMQ_HOST"));
        factory.setPort(requiredPort("RABBITMQ_PORT"));
        factory.setUsername(requiredEnv("RABBITMQ_USERNAME"));
        factory.setPassword(requiredEnv("RABBITMQ_PASSWORD"));
        factory.setConnectionTimeout(10_000);
        factory.setHandshakeTimeout(10_000);

        byte[] payload = "ai-travel-config-ok".getBytes(StandardCharsets.UTF_8);
        try (Connection connection = factory.newConnection("ai-travel-config-test");
                Channel channel = connection.createChannel()) {
            String queue = channel.queueDeclare("", false, true, true, null).getQueue();
            channel.basicPublish("", queue, null, payload);
            GetResponse response = channel.basicGet(queue, true);

            assertThat(response).isNotNull();
            assertThat(response.getBody()).isEqualTo(payload);
            assertThat(channel.isOpen()).isTrue();
        }
    }
}
