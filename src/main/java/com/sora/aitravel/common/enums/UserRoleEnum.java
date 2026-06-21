package com.sora.aitravel.common.enums;

/**
 * 数据库用户角色枚举。
 * <p>
 * USER = 1（普通用户）、ADMIN = 2（管理员）。
 * 持久化时应使用 name()，不要使用 ordinal()，避免因枚举顺序变更导致数据错乱。
 * </p>
 */
public enum UserRoleEnum {
    /** 普通用户。 */
    USER,
    /** 管理员。 */
    ADMIN
}
