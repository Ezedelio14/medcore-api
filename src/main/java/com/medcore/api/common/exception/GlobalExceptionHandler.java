package com.medcore.api.common.exception;

import com.medcore.api.common.web.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into RFC 9457 {@link ProblemDetail} responses.
 * Every response carries a stable machine-readable {@code code}, the request correlation id and a timestamp.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DOUBLE_BOOKING_CONSTRAINT = "ex_appointments_no_double_booking";

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("Business failure: {}", ex.getMessage(), ex);
        } else {
            log.debug("Business failure: {} - {}", ex.getCode(), ex.getMessage());
        }
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleMalformedRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request body or parameter");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains(DOUBLE_BOOKING_CONSTRAINT)) {
            return problem(HttpStatus.CONFLICT, "SLOT_UNAVAILABLE", "The selected time slot is no longer available");
        }
        log.warn("Data integrity violation: {}", message);
        return problem(HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with existing data");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource was modified concurrently, please reload and retry");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ProblemDetail handleAccessDenied(Exception ex) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to perform this action");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        // Spring MVC's own exceptions (missing header, unsupported method, 404...) carry their status.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return problem(status, status.name(), String.valueOf(errorResponse.getBody().getDetail()));
        }
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    public static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://api.medcore.health/errors/" + code.toLowerCase().replace('_', '-')));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
