package com.medcore.api.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }
}
