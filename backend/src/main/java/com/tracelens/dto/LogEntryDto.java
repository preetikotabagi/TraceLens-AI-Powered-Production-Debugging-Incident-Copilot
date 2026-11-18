package com.tracelens.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record LogEntryDto(
        Long id,
        Instant timestamp,
        @NotNull(message = "level is required") LogLevel level,
        @NotBlank(message = "serviceName is required") String serviceName,
        String environment,
        @NotBlank(message = "message is required") String message,
        String logger,
        String threadName,
        String requestId,
        String userId,
        String traceId,
        String spanId,
        String errorType,
        String stackTrace,
        String fingerprint,
        Long incidentId,
        String metadata) {
}
