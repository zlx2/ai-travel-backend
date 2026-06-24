package com.sora.aitravel.common.constants;

import java.util.Set;

/**
 * 文件上传相关常量。
 *
 * <p>定义头像、封面图等文件的大小限制和允许的文件扩展名。
 */
public final class FileConstants {
    /** 头像文件最大大小，2MB。 */
    public static final long AVATAR_MAX_SIZE = 2 * 1024 * 1024L;

    /** 游记封面图最大大小，5MB。 */
    public static final long NOTE_COVER_MAX_SIZE = 5 * 1024 * 1024L;

    /** 允许上传的图片文件扩展名集合。 */
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    /** 工具类，防止实例化。 */
    private FileConstants() {}
}
