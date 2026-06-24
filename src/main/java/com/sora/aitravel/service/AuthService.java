package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.LoginRequest;
import com.sora.aitravel.dto.request.RegisterRequest;
import com.sora.aitravel.dto.response.LoginResponse;

/**
 * 认证服务接口。
 *
 * <p>提供用户注册、登录和退出登录的认证相关功能。
 */
public interface AuthService {
    /**
     * 用户注册。
     *
     * @param request 注册请求，包含用户名、密码、邮箱和验证码
     * @return 新注册用户的 ID
     */
    Long register(RegisterRequest request);

    /**
     * 用户登录。
     *
     * @param request 登录请求，包含用户名和密码
     * @return 登录响应，包含 Token 和用户基本信息
     */
    LoginResponse login(LoginRequest request);

    /**
     * 退出登录。
     *
     * <p>清除当前用户的登录状态和 Token。
     */
    void logout();
}
