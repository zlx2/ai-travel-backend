package com.sora.aitravel.common.constants;

/**
 * 认证相关常量。
 * <p>
 * 定义 Token 请求头、角色值等认证授权相关的常量。
 * </p>
 */
public final class AuthConstants {
    /** HTTP 请求头中携带 Token 的字段名。 */
    public static final String TOKEN_HEADER = "Authorization";
    /** Token 值的前缀标识。 */
    public static final String BEARER_PREFIX = "Bearer ";
    /** 普通用户角色值，对应数据库 sys_user.role = 1。 */
    public static final int USER_ROLE = 1;
    /** 管理员角色值，对应数据库 sys_user.role = 2。 */
    public static final int ADMIN_ROLE = 2;

    /** 工具类，防止实例化。 */
    private AuthConstants() {}
}
