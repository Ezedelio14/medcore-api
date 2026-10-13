package com.medcore.api.common.exception;

import org.springframework.http.HttpStatus;

/** Failure of a third-party integration (Stripe, SMTP, storage...). */
public class ExternalServiceException extends BusinessException {

    public ExternalServiceException(String service, String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "EXTERNAL_SERVICE_ERROR", "%s: %s".formatted(service, message));
        initCause(cause);
    }
}
