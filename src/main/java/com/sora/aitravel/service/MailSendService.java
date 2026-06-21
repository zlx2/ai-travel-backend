package com.sora.aitravel.service;

/**
 * 邮件发送服务接口。
 * <p>
 * 提供发送电子邮件的功能，目前支持发送验证码邮件。
 * </p>
 */
public interface MailSendService {
    /**
     * 发送邮箱验证码邮件。
     *
     * @param email 收件人邮箱地址
     * @param code  验证码内容
     */
    void sendVerificationCode(String email, String code);
}
