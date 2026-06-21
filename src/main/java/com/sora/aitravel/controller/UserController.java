package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.UpdateUserProfileRequest;
import com.sora.aitravel.dto.response.UserInfoResponse;
import com.sora.aitravel.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@SaCheckLogin
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public R<UserInfoResponse> me() {
        return R.ok(userService.getCurrentUser());
    }

    @PutMapping("/me")
    public R<Void> update(@Valid @RequestBody UpdateUserProfileRequest request) {
        userService.updateCurrentUser(request);
        return R.ok();
    }
}
