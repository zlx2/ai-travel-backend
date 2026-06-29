package com.sora.aitravel.service.impl;

import static com.sora.aitravel.common.constants.RedisKeyConstants.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.exception.BusinessException;
import com.sora.aitravel.entity.SysUser;
import com.sora.aitravel.mapper.SysUserMapper;
import com.sora.aitravel.service.EmailCodeService;
import com.sora.aitravel.service.MailSendService;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final String REGISTER_SCENE = "register";
    private static final String CHANGE_EMAIL_SCENE = "change_email";
    private static final String RESET_PASSWORD_SCENE = "reset_password";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final MailSendService mailSendService;
    private final SysUserMapper userMapper;

    @Override
    public void send(String email, String scene) {
        String normalizedEmail = normalizeEmail(email);
        validateScene(scene);

        // 注册或修改邮箱场景：发送验证码前先校验邮箱是否已被注册，避免无效请求占用限流窗口。
        // 找回密码场景：邮箱必须已注册。
        if (REGISTER_SCENE.equals(scene) || CHANGE_EMAIL_SCENE.equals(scene)) {
            Long count =
                    userMapper.selectCount(
                            new LambdaQueryWrapper<SysUser>()
                                    .eq(SysUser::getEmail, normalizedEmail));
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
        } else if (RESET_PASSWORD_SCENE.equals(scene)) {
            Long count =
                    userMapper.selectCount(
                            new LambdaQueryWrapper<SysUser>()
                                    .eq(SysUser::getEmail, normalizedEmail));
            if (count == null || count == 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "该邮箱未注册");
            }
        }

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
            // 验证码必须通过真实 SMTP 投递，不提供固定验证码或绕过发送的模拟分支。
            mailSendService.sendVerificationCode(normalizedEmail, code);
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
        if (!REGISTER_SCENE.equals(scene) && !CHANGE_EMAIL_SCENE.equals(scene) && !RESET_PASSWORD_SCENE.equals(scene)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的验证码场景：" + scene);
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
