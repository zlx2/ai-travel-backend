package com.sora.aitravel.common.constants;

public final class RedisKeyConstants {
    /** 验证码完整 Key：email:code:{scene}:{email}。 */
    public static final String EMAIL_CODE_PREFIX = "email:code:";

    /** 发送限流完整 Key：email:code:limit:{scene}:{email}。 */
    public static final String EMAIL_CODE_LIMIT_PREFIX = "email:code:limit:";

    /** AI 会话完整 Key：ai:conversation:{conversationId}。 */
    public static final String AI_CONVERSATION_PREFIX = "ai:conversation:";

    public static final long EMAIL_CODE_TTL_MINUTES = 5;
    public static final long EMAIL_CODE_LIMIT_SECONDS = 60;
    public static final long AI_CONVERSATION_TTL_HOURS = 24;

    private RedisKeyConstants() {}
}
