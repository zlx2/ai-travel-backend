package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.*;
import com.sora.aitravel.dto.request.UpdateUserProfileRequest;
import com.sora.aitravel.dto.response.UserInfoResponse;
import com.sora.aitravel.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户个人中心控制器。
 * <p>接口前缀：/api/users</p>
 * <p>请求方式：RESTful</p>
 * <p>权限要求：所有接口均需登录（@SaCheckLogin）</p>
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /**
     * 获取当前登录用户的个人信息。
     *
     * @return 当前用户的详细信息（UserInfoResponse）
     */
    @GetMapping("/me")
    public R<UserInfoResponse> me() {
        return R.ok(userService.getCurrentUser());
    }

    /**
     * 更新当前登录用户的个人资料。
     *
     * @param request 包含昵称和头像 URL 的更新请求
     * @return 无返回内容，操作成功即返回成功响应
     */
    @PutMapping("/me")
    public R<Void> update(@Valid @RequestBody UpdateUserProfileRequest request) {
        userService.updateCurrentUser(request);
        return R.ok();
    }
}
