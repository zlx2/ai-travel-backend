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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException exception) {
        return R.fail(exception.getErrorCode(), exception.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class
    })
    public R<Void> handleValidation(Exception exception) {
        // 不直接返回框架异常文本，避免泄露内部类名、字段路径等实现细节。
        return R.fail(ErrorCode.PARAM_ERROR);
    }

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException exception) {
        return R.fail(ErrorCode.NOT_LOGIN);
    }

    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException exception) {
        return R.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleUnexpected(Exception exception) {
        log.error("Unhandled server error", exception);
        return R.fail(ErrorCode.SYSTEM_ERROR);
    }
}
