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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    @PostMapping("/email-code")
    public R<Void> sendCode(@Valid @RequestBody EmailCodeRequest request) {
        emailCodeService.send(request.email(), request.scene());
        return R.ok();
    }

    @PostMapping("/register")
    public R<IdResponse> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(new IdResponse(authService.register(request)));
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @SaCheckLogin
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }
}
