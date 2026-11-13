package com.tracelens.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors the application raises on purpose and wants reported to the client
 * with a specific HTTP status and a stable machine-readable code.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
