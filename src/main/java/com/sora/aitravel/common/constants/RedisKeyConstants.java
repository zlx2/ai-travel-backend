package com.sora.aitravel.common.constants;

/**
 * Redis 键名常量。
 * <p>
 * 集中管理 Redis 中使用的所有 Key 前缀和过期时间常量，
 * 避免在业务代码中分散定义。
 * </p>
 */
public final class RedisKeyConstants {
    /** 验证码完整 Key：email:code:{scene}:{email}。 */
    public static final String EMAIL_CODE_PREFIX = "email:code:";

    /** 发送限流完整 Key：email:code:limit:{scene}:{email}。 */
    public static final String EMAIL_CODE_LIMIT_PREFIX = "email:code:limit:";

    /** AI 会话完整 Key：ai:conversation:{conversationId}。 */
    public static final String AI_CONVERSATION_PREFIX = "ai:conversation:";

    /** 邮箱验证码过期时间，单位分钟。 */
    public static final long EMAIL_CODE_TTL_MINUTES = 5;
    /** 邮箱验证码发送限流时间间隔，单位秒。 */
    public static final long EMAIL_CODE_LIMIT_SECONDS = 60;
    /** AI 会话缓存过期时间，单位小时。 */
    public static final long AI_CONVERSATION_TTL_HOURS = 24;

    /** 工具类，防止实例化。 */
    private RedisKeyConstants() {}
}
