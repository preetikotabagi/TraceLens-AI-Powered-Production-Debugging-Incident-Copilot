package com.tracelens.dto;

import java.time.Instant;

public record ServiceHealthDto(
        String serviceName,
        long totalLogs,
        long errorCount,
        double errorRate,
        String status,
        Instant lastSeen) {
}
