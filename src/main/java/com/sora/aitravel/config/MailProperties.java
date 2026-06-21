package com.sora.aitravel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮件发送配置属性。
 * <p>
 * 从配置文件 app.email.* 中读取邮件相关参数。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.email")
public class MailProperties {
    /** 测试环境使用的固定验证码，方便本地调试时绕过真实邮件发送。 */
    private String mockCode;
}
