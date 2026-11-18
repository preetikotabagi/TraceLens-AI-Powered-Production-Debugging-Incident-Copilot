package com.tracelens.dto;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        String title,
        String serviceName,
        Severity severity,
        IncidentStatus status,
        String fingerprint,
        String triggerRule,
        int occurrenceCount,
        Instant firstSeen,
        Instant lastSeen,
        String summary,
        Instant createdAt,
        Instant updatedAt) {
}
