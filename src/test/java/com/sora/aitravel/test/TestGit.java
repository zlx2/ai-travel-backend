package com.sora.aitravel.test;

/**
 * @author: ljw
 * @date: 2026/6/22 10:00
 * @version: v1.0.0
 * @description:
 **/

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.config.MailProperties;
import com.sora.aitravel.service.MailSendService;
import java.time.Duration;

import com.sora.aitravel.service.impl.EmailCodeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class EmailCodeServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private MailSendService mailSendService;

    private MailProperties properties;
    private EmailCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new MailProperties();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new EmailCodeServiceImpl(redisTemplate, mailSendService, properties);
    }

    @Test
    void mockCodeShouldUseDocumentedTtlWithoutSendingMail() {
        properties.setMockCode("123456");
        when(valueOperations.setIfAbsent(any(), eq("1"), any(Duration.class))).thenReturn(true);

        service.send("TEST@example.com", "register");

        verify(valueOperations)
                .set("email:code:register:test@example.com", "123456", Duration.ofMinutes(5));
        verify(mailSendService, never()).sendVerificationCode(any(), any());
    }

    @Test
    void missingCodeShouldReturnExpiredError() {
        when(valueOperations.get("email:code:register:test@example.com")).thenReturn(null);

        assertThatThrownBy(() -> service.verify("test@example.com", "register", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_CODE_EXPIRED);
    }
}
