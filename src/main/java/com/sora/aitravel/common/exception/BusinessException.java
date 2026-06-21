package com.sora.aitravel.common.exception;

import com.sora.aitravel.common.enums.ErrorCode;
import lombok.Getter;

/**
 * 业务异常类。
 * <p>
 * 统一业务层抛出的异常，包含 {@link ErrorCode} 错误码，
 * 由 {@link GlobalExceptionHandler} 全局捕获并返回标准响应格式。
 * </p>
 */
@Getter
public class BusinessException extends RuntimeException {
    /** 业务错误码。 */
    private final ErrorCode errorCode;

    /**
     * 使用错误码构造异常，消息内容使用错误码的默认描述。
     *
     * @param errorCode 业务错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码和自定义消息构造异常，覆盖默认的错误描述。
     *
     * @param errorCode 业务错误码
     * @param message   自定义错误描述
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
