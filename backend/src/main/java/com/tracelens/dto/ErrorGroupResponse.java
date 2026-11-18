package com.tracelens.dto;

import java.time.Instant;

public record ErrorGroupResponse(
        String fingerprint,
        String errorType,
        String serviceName,
        String messageTemplate,
        String sampleStackTrace,
        int occurrenceCount,
        Instant firstSeen,
        Instant lastSeen,
        Instant createdAt,
        Instant updatedAt) {
}
