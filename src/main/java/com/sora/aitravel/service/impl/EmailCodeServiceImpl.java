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

/**
 * 邮箱验证码服务实现
 * 提供邮箱验证码的发送、验证和核销功能，支持注册、修改邮箱、重置密码等场景
 */
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

    /**
     * 发送邮箱验证码
     *
     * @param email 邮箱地址
     * @param scene 场景（register/change_email/reset_password）
     */
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

    /**
     * 验证邮箱验证码
     *
     * @param email 邮箱地址
     * @param scene 场景
     * @param code  验证码
     */
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

    /**
     * 删除邮箱验证码（核销）
     *
     * @param email 邮箱地址
     * @param scene 场景
     */
    @Override
    public void remove(String email, String scene) {
        redisTemplate.delete(codeKey(scene, normalizeEmail(email)));
    }

    /**
     * 生成6位数字验证码
     *
     * @return 6位验证码字符串
     */
    private String createCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    /**
     * 验证场景是否支持
     *
     * @param scene 场景
     * @throws BusinessException 如果场景不支持
     */
    private void validateScene(String scene) {
        if (!REGISTER_SCENE.equals(scene)
                && !CHANGE_EMAIL_SCENE.equals(scene)
                && !RESET_PASSWORD_SCENE.equals(scene)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的验证码场景：" + scene);
        }
    }

    /**
     * 规范化邮箱地址（去除空格并转小写）
     *
     * @param email 原始邮箱地址
     * @return 规范化后的邮箱地址
     */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 生成验证码缓存键
     *
     * @param scene 场景
     * @param email 邮箱地址
     * @return 缓存键
     */
    private String codeKey(String scene, String email) {
        return EMAIL_CODE_PREFIX + scene + ":" + email;
    }

    /**
     * 生成限流缓存键
     *
     * @param scene 场景
     * @param email 邮箱地址
     * @return 限流键
     */
    private String limitKey(String scene, String email) {
        return EMAIL_CODE_LIMIT_PREFIX + scene + ":" + email;
    }
}