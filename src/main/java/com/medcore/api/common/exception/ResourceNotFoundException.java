package com.medcore.api.common.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resource, UUID id) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "%s %s not found".formatted(resource, id));
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }
}
