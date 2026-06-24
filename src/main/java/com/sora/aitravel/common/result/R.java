package com.sora.aitravel.common.result;

import com.sora.aitravel.common.enums.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应结果封装。
 *
 * <p>所有 Controller 返回给前端的数据都使用此类包裹， 包含状态码（code）、提示信息（message）和业务数据（data）。
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R<T> {
    /** 状态码，200 表示成功，其余为错误码。 */
    private Integer code;

    /** 提示信息。 */
    private String message;

    /** 业务数据。 */
    private T data;

    /**
     * 请求成功，返回数据和默认成功消息。
     *
     * @param data 业务数据
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> R<T> ok(T data) {
        return new R<>(200, "success", data);
    }

    /**
     * 请求成功，不返回数据。
     *
     * @param <T> 数据类型
     * @return 成功响应
     */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /**
     * 请求失败，使用错误码中的 code 和 message。
     *
     * @param errorCode 业务错误码
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 请求失败，使用错误码的 code 和自定义 message。
     *
     * @param errorCode 业务错误码
     * @param message 自定义错误消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return new R<>(errorCode.getCode(), message, null);
    }

    /**
     * 请求失败，使用自定义 code 和 message。
     *
     * @param code 自定义状态码
     * @param message 错误消息
     * @param <T> 数据类型
     * @return 失败响应
     */
    public static <T> R<T> fail(Integer code, String message) {
        return new R<>(code, message, null);
    }
}
