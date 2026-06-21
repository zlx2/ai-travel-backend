package com.sora.aitravel.common.utils;

import cn.dev33.satoken.stp.StpUtil;

public final class LoginUserUtils {
    private LoginUserUtils() {}

    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    public static boolean isAdmin() {
        return StpUtil.hasRole("2");
    }
}
