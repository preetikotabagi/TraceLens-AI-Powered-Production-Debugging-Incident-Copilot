package com.tracelens.dto;

import java.time.Instant;

public record SuspiciousEventDto(
        Instant timestamp,
        String service,
        String event,
        String suspicionReason) {
}
