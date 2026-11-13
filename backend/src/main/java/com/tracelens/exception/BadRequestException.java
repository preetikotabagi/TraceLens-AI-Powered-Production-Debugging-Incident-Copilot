package com.tracelens.exception;

import org.springframework.http.HttpStatus;

/**
 * The client sent a request that is syntactically valid but not acceptable (HTTP 400).
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
