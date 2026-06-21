package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.LoginResponse;
import com.sora.aitravel.service.AuthService;
import com.sora.aitravel.service.EmailCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证控制器。
 * <p>接口前缀：/api/auth</p>
 * <p>请求方式：RESTful</p>
 * <p>权限要求：注册/登录/发送验证码无需登录；登出需登录（@SaCheckLogin）</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    /**
     * 发送邮箱验证码。
     *
     * @param request 包含邮箱地址和场景（scene）的请求体
     * @return 无返回内容，验证码发送成功即返回成功响应
     */
    @PostMapping("/email-code")
    public R<Void> sendCode(@Valid @RequestBody EmailCodeRequest request) {
        emailCodeService.send(request.email(), request.scene());
        return R.ok();
    }

    /**
     * 用户注册。
     *
     * @param request 包含用户名、密码、邮箱和邮箱验证码的注册请求
     * @return 注册成功返回新用户的 ID（IdResponse）
     */
    @PostMapping("/register")
    public R<IdResponse> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(new IdResponse(authService.register(request)));
    }

    /**
     * 用户登录。
     *
     * @param request 包含账号和密码的登录请求
     * @return 登录成功返回 Token 和用户信息（LoginResponse）
     */
    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /**
     * 用户登出。
     * <p>需要登录态。</p>
     *
     * @return 无返回内容，登出成功即返回成功响应
     */
    @SaCheckLogin
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }
}
