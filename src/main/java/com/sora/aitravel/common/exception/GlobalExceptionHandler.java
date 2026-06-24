package com.sora.aitravel.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import com.sora.aitravel.common.enums.ErrorCode;
import com.sora.aitravel.common.result.R;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>通过 {@link RestControllerAdvice} 统一捕获 Controller 层抛出的各类异常， 转换为统一的 {@link R}
 * 响应格式返回给前端，避免将异常堆栈暴露出去。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常
     * @return 包含错误码和消息的统一响应
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException exception) {
        return R.fail(exception.getErrorCode(), exception.getMessage());
    }

    /**
     * 处理参数校验异常（MethodArgumentNotValidException / BindException / ConstraintViolationException）。
     *
     * <p>不直接返回框架异常文本，避免泄露内部类名、字段路径等实现细节。
     *
     * @param exception 参数校验异常
     * @return 参数错误的统一响应
     */
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class
    })
    public R<Void> handleValidation(Exception exception) {
        // 不直接返回框架异常文本，避免泄露内部类名、字段路径等实现细节。
        return R.fail(ErrorCode.PARAM_ERROR);
    }

    /**
     * 处理用户未登录异常（Sa-Token）。
     *
     * @param exception 未登录异常
     * @return 未登录的错误响应
     */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException exception) {
        return R.fail(ErrorCode.NOT_LOGIN);
    }

    /**
     * 处理用户无角色权限异常（Sa-Token）。
     *
     * @param exception 无权限异常
     * @return 无权限的错误响应
     */
    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException exception) {
        return R.fail(ErrorCode.FORBIDDEN);
    }

    /**
     * 处理所有未预料的异常（兜底处理）。
     *
     * <p>记录完整异常堆栈到日志，防止生产环境丢失排查线索。
     *
     * @param exception 未预料的异常
     * @return 系统错误的统一响应
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleUnexpected(Exception exception) {
        log.error("Unhandled server error", exception);
        return R.fail(ErrorCode.SYSTEM_ERROR);
    }
}
