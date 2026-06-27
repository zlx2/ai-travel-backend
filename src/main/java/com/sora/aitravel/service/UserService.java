package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.SendChangeEmailCodeRequest;
import com.sora.aitravel.dto.request.UpdateUserEmailRequest;
import com.sora.aitravel.dto.request.UpdateUserProfileRequest;
import com.sora.aitravel.dto.response.UserInfoResponse;
import com.sora.aitravel.dto.response.UserProfileStatsResponse;

/**
 * 用户服务接口。
 *
 * <p>提供当前登录用户的基本信息查询、个人资料更新和邮箱修改功能。
 */
public interface UserService {
    /**
     * 获取当前登录用户的信息。
     *
     * @return 当前用户信息，包含用户名、头像、邮箱等
     */
    UserInfoResponse getCurrentUser();

    /**
     * 获取当前登录用户的个人中心统计数据。
     *
     * @return 当前用户的行程、游记、获赞和收藏统计
     */
    UserProfileStatsResponse getCurrentUserStats();

    /**
     * 更新当前登录用户的个人资料。
     *
     * @param request 更新用户资料请求
     */
    void updateCurrentUser(UpdateUserProfileRequest request);

    /**
     * 发送修改邮箱验证码。
     *
     * @param request 包含新邮箱地址的请求
     */
    void sendChangeEmailCode(SendChangeEmailCodeRequest request);

    /**
     * 确认修改当前登录用户的邮箱。
     *
     * @param request 包含新邮箱和验证码的请求
     */
    void updateCurrentUserEmail(UpdateUserEmailRequest request);
}
