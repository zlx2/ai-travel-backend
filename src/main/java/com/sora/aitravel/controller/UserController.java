package com.sora.aitravel.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sora.aitravel.common.result.R;
import com.sora.aitravel.dto.request.SendChangeEmailCodeRequest;
import com.sora.aitravel.dto.request.UpdateUserEmailRequest;
import com.sora.aitravel.dto.request.UpdateUserProfileRequest;
import com.sora.aitravel.dto.response.UserInfoResponse;
import com.sora.aitravel.dto.response.UserProfileStatsResponse;
import com.sora.aitravel.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户个人中心控制器。
 *
 * <p>接口前缀：/api/users
 *
 * <p>请求方式：RESTful
 *
 * <p>权限要求：所有接口均需登录（@SaCheckLogin）
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
     * 获取当前登录用户的个人中心统计数据。
     *
     * @return 当前用户的行程、游记、获赞和收藏统计
     */
    @GetMapping("/me/stats")
    public R<UserProfileStatsResponse> stats() {
        return R.ok(userService.getCurrentUserStats());
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

    /**
     * 发送修改邮箱验证码到新邮箱。
     *
     * @param request 包含新邮箱地址的请求
     * @return 无返回内容，验证码发送成功即返回成功响应
     */
    @PostMapping("/me/email-code")
    public R<Void> sendChangeEmailCode(@Valid @RequestBody SendChangeEmailCodeRequest request) {
        userService.sendChangeEmailCode(request);
        return R.ok();
    }

    /**
     * 确认修改当前登录用户的邮箱。
     *
     * @param request 包含新邮箱和验证码的请求
     * @return 无返回内容，邮箱修改成功即返回成功响应
     */
    @PutMapping("/me/email")
    public R<Void> updateEmail(@Valid @RequestBody UpdateUserEmailRequest request) {
        userService.updateCurrentUserEmail(request);
        return R.ok();
    }
}
