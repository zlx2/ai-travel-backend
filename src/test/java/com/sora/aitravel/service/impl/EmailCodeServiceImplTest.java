package com.sora.aitravel.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.service.MailSendService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class EmailCodeServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private MailSendService mailSendService;
    @Mock private SysUserMapper userMapper;

    private EmailCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new EmailCodeServiceImpl(redisTemplate, mailSendService, userMapper);
    }

    @Test
    void sendShouldDeliverRealCodeAndCacheItWithDocumentedTtl() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(valueOperations.setIfAbsent(any(), eq("1"), any(Duration.class))).thenReturn(true);

        service.send("TEST@example.com", "register");

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSendService).sendVerificationCode(eq("test@example.com"), codeCaptor.capture());
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        verify(valueOperations)
                .set(
                        "email:code:register:test@example.com",
                        codeCaptor.getValue(),
                        Duration.ofMinutes(5));
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
