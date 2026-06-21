package com.sora.aitravel.service;

public interface MailSendService {
    void sendVerificationCode(String email, String code);
}
