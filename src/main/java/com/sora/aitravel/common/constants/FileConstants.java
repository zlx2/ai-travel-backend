package com.sora.aitravel.common.constants;

import java.util.Set;

public final class FileConstants {
    public static final long AVATAR_MAX_SIZE = 2 * 1024 * 1024L;
    public static final long NOTE_COVER_MAX_SIZE = 5 * 1024 * 1024L;
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private FileConstants() {}
}
