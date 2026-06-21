package com.sora.aitravel.integration;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@EnabledIfSystemProperty(named = ExternalIntegrationTestSupport.ENABLE_PROPERTY, matches = "true")
class SmtpIntegrationTest extends ExternalIntegrationTestSupport {

    @Test
    void smtpAcceptsCredentialsWithoutSendingEmail() throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(requiredEnv("MAIL_HOST"));
        sender.setPort(requiredPort("MAIL_PORT"));
        sender.setUsername(requiredEnv("MAIL_USERNAME"));
        sender.setPassword(requiredEnv("MAIL_PASSWORD"));

        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.ssl.enable", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "10000");

        // testConnection 只完成连接和认证，不发送邮件。
        sender.testConnection();
    }
}
