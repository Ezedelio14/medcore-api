package com.medcore.api.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base class for expected, domain-level failures that map to a 4xx response. */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public BusinessException(String code, String message) {
        this(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
