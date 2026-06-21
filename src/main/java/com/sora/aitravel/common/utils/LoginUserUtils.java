package com.sora.aitravel.common.utils;

import cn.dev33.satoken.stp.StpUtil;

/**
 * 当前登录用户工具类。
 * <p>
 * 封装 Sa-Token 的常用操作，提供获取当前登录用户 ID、判断是否为管理员等便捷方法。
 * </p>
 */
public final class LoginUserUtils {
    private LoginUserUtils() {}

    /**
     * 获取当前登录用户的 ID。
     *
     * @return 当前登录用户的 Long 类型 ID
     * @throws cn.dev33.satoken.exception.NotLoginException 如果用户未登录
     */
    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 判断当前登录用户是否为管理员。
     *
     * @return 如果是管理员返回 true，否则返回 false
     */
    public static boolean isAdmin() {
        return StpUtil.hasRole("2");
    }
}
