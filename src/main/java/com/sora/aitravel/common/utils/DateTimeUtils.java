package com.sora.aitravel.common.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具类。
 * <p>
 * 提供 LocalDateTime 与指定格式字符串之间的转换方法。
 * </p>
 */
public final class DateTimeUtils {
    /** 默认日期时间格式：yyyy-MM-dd HH:mm:ss。 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DateTimeUtils() {}

    /**
     * 将 LocalDateTime 格式化为 "yyyy-MM-dd HH:mm:ss" 格式的字符串。
     *
     * @param value 待格式化的日期时间，可为 null
     * @return 格式化后的字符串，如果传入 null 则返回 null
     */
    public static String format(LocalDateTime value) {
        return value == null ? null : FORMATTER.format(value);
    }
}
