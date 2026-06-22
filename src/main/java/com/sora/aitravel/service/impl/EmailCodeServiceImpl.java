package com.sora.aitravel.service.impl;

import static com.sora.aitravel.common.constants.RedisKeyConstants.*;

import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.config.MailProperties;
import com.sora.aitravel.service.EmailCodeService;
import com.sora.aitravel.service.MailSendService;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final String REGISTER_SCENE = "register";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final MailSendService mailSendService;
    private final MailProperties mailProperties;

    @Override
    public void send(String email, String scene) {
        String normalizedEmail = normalizeEmail(email);
        validateScene(scene);
        String limitKey = limitKey(scene, normalizedEmail);
        // Redis SET NX 形成发送冷却窗口，拦截重复点击和批量滥发。
        Boolean allowed =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(limitKey, "1", Duration.ofSeconds(EMAIL_CODE_LIMIT_SECONDS));
        if (!Boolean.TRUE.equals(allowed)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "验证码发送过于频繁，请稍后再试");
        }

        String code = createCode();
        String codeKey = codeKey(scene, normalizedEmail);
        try {
            // 只有显式配置固定码时才跳过 SMTP；默认始终投递真实邮件。
            if (!StringUtils.hasText(mailProperties.getMockCode())) {
                mailSendService.sendVerificationCode(normalizedEmail, code);
            } else {
                // 最终版需求允许开发环境显式配置固定验证码；未配置时始终走真实 SMTP。
                code = mailProperties.getMockCode().trim();
            }
            redisTemplate
                    .opsForValue()
                    .set(codeKey, code, Duration.ofMinutes(EMAIL_CODE_TTL_MINUTES));
        } catch (RuntimeException exception) {
            // 投递或缓存失败时撤销限流，修复配置后可以立即重试。
            redisTemplate.delete(limitKey);
            throw exception;
        }
    }

    @Override
    public void verify(String email, String scene, String code) {
        // 此处只校验、不删除；注册事务成功后才核销，失败时用户仍可重试。
        String key = codeKey(scene, normalizeEmail(email));
        String savedCode = redisTemplate.opsForValue().get(key);
        if (savedCode == null) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (!savedCode.equalsIgnoreCase(code.trim())) {
            throw new BusinessException(ErrorCode.EMAIL_CODE_ERROR);
        }
    }

    @Override
    public void remove(String email, String scene) {
        redisTemplate.delete(codeKey(scene, normalizeEmail(email)));
    }

    private String createCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private void validateScene(String scene) {
        if (!REGISTER_SCENE.equals(scene)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "一期仅支持注册验证码");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String codeKey(String scene, String email) {
        return EMAIL_CODE_PREFIX + scene + ":" + email;
    }

    private String limitKey(String scene, String email) {
        return EMAIL_CODE_LIMIT_PREFIX + scene + ":" + email;
    }
}
