package com.tracelens.exception;

import java.time.Instant;
import java.util.List;

/**
 * The single JSON error shape returned by every failing API call.
 *
 * @param code      stable machine-readable identifier, e.g. VALIDATION_FAILED
 * @param message   human-readable summary that never contains stack traces or internals
 * @param details   per-field problems for validation errors, otherwise empty
 * @param requestId correlates the response with the server log lines of the same request
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldIssue> details,
        String requestId) {

    public record FieldIssue(String field, String message) {
    }
}
