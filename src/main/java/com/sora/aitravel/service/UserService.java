package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.UpdateUserProfileRequest;
import com.sora.aitravel.dto.response.UserInfoResponse;

/**
 * 用户服务接口。
 *
 * <p>提供当前登录用户的基本信息查询和个人资料更新功能。
 */
public interface UserService {
    /**
     * 获取当前登录用户的信息。
     *
     * @return 当前用户信息，包含用户名、头像、邮箱等
     */
    UserInfoResponse getCurrentUser();

    /**
     * 更新当前登录用户的个人资料。
     *
     * @param request 更新用户资料请求
     */
    void updateCurrentUser(UpdateUserProfileRequest request);
}
