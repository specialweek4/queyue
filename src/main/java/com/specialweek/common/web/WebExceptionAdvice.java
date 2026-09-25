package com.specialweek.common.web;

import com.specialweek.auth.exception.AuthException;
import com.specialweek.common.except.BusinessException;
import com.specialweek.common.web.Result;
import com.specialweek.limiter.exception.RateLimitException;
import com.specialweek.storage.exception.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author specialweek
 * @since 2026-08-15
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result handleIllegalArgumentException(IllegalArgumentException e) {
        return Result.fail(e.getMessage() == null ? "参数错误" : e.getMessage());
    }

    @ExceptionHandler(AuthException.class)
    public Result handleAuthException(AuthException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(RateLimitException.class)
    public Result handleRateLimitException(RateLimitException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数校验失败"
                : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return Result.fail(message == null ? "参数校验失败" : message);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result handleAccessDeniedException(AccessDeniedException e) {
        return Result.fail("没有访问权限");
    }

    @ExceptionHandler(StorageException.class)
    public Result handleStorageException(StorageException e) {
        return Result.fail(e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public Result handleStorageException(BusinessException e) {
        return Result.fail(e.getMessage());
    }
}
