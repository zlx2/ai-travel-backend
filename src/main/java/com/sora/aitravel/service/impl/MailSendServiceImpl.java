package com.sora.aitravel.service.impl;

import com.sora.aitravel.service.MailSendService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** SMTP 投递适配器，让认证业务无需感知具体邮件服务商。 */
@Service
@RequiredArgsConstructor
public class MailSendServiceImpl implements MailSendService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String sender;

    @Override
    public void sendVerificationCode(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(email);
        message.setSubject("AI智行伴旅邮箱验证码");
        message.setText("您的注册验证码是：" + code + "。验证码 5 分钟内有效，请勿泄露给他人。");
        mailSender.send(message);
    }
}
