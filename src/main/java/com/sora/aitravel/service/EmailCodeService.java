package com.sora.aitravel.service;

/**
 * 邮箱验证码服务接口。
 *
 * <p>提供邮箱验证码的发送、校验和移除功能，用于注册、找回密码等场景。
 */
public interface EmailCodeService {
    /**
     * 发送邮箱验证码。
     *
     * @param email 目标邮箱地址
     * @param scene 验证码使用场景，如 "register"、"reset_password"
     */
    void send(String email, String scene);

    /**
     * 校验邮箱验证码是否正确。
     *
     * @param email 邮箱地址
     * @param scene 验证码场景
     * @param code 用户输入的验证码
     * @throws com.sora.aitravel.common.exception.BusinessException 验证码不正确或已过期时抛出
     */
    void verify(String email, String scene, String code);

    /**
     * 移除邮箱验证码（使用后清除）。
     *
     * @param email 邮箱地址
     * @param scene 验证码场景
     */
    void remove(String email, String scene);
}
