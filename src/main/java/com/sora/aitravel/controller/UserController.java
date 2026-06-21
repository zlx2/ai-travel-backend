package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.UpdateUserProfileRequest;
import com.sora.aitravel.dto.response.UserInfoResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@RestController
@RequestMapping("/api/users")
public class UserController {
    @GetMapping("/me")
    public R<UserInfoResponse> me() {
        return ScaffoldResponses.notImplemented();
    }

    @PutMapping("/me")
    public R<Void> update(@Valid @RequestBody UpdateUserProfileRequest request) {
        return ScaffoldResponses.notImplemented();
    }
}
