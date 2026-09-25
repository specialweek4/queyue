package com.specialweek.common.except;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
