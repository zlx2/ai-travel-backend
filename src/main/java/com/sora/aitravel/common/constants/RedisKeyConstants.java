package com.sora.aitravel.common.constants;

public final class RedisKeyConstants {
    public static final String EMAIL_CODE_PREFIX = "email:code:";

    public static final String EMAIL_CODE_LIMIT_PREFIX = "email:code:limit:";

    public static final long EMAIL_CODE_TTL_MINUTES = 5;

    public static final long EMAIL_CODE_LIMIT_SECONDS = 60;

    private RedisKeyConstants() {}
}
