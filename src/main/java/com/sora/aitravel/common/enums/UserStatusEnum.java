package com.sora.aitravel.common.enums;

/**
 * 数据库用户状态枚举。
 * <p>
 * DISABLED = 0（禁用），ENABLED = 1（启用）。
 * 持久化时应使用 name() 而非 ordinal()，避免新增枚举值影响顺序。
 * </p>
 */
public enum UserStatusEnum {
    /** 禁用。 */
    DISABLED,
    /** 启用。 */
    ENABLED
}
