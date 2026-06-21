package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.*;
import com.sora.aitravel.dto.response.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @PostMapping("/email-code")
    public R<Void> sendCode(@Valid @RequestBody EmailCodeRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @PostMapping("/register")
    public R<IdResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ScaffoldResponses.notImplemented();
    }

    @SaCheckLogin
    @PostMapping("/logout")
    public R<Void> logout() {
        return ScaffoldResponses.notImplemented();
    }
}
