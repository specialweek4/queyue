package com.specialweek.common.web;

import com.specialweek.auth.exception.AuthException;
import com.specialweek.common.web.Result;
import lombok.extern.slf4j.Slf4j;
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

    @ExceptionHandler(AuthException.class)
    public Result handleAuthException(AuthException e) {
        return Result.fail(e.getMessage());
    }
}
