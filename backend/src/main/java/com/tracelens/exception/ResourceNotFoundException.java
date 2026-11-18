package com.tracelens.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an entity (incident, error group, log) is not found.
 */
public class ResourceNotFoundException extends ApiException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }
}
