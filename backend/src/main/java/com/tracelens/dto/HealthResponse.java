package com.tracelens.dto;

import java.time.Instant;

/**
 * Response of {@code GET /api/v1/health}.
 */
public record HealthResponse(
        Status status,
        ApplicationInfo application,
        DatabaseInfo database,
        Instant timestamp) {

    public enum Status {
        UP,
        DOWN
    }

    public record ApplicationInfo(String name, String version) {
    }

    /**
     * @param latencyMs        round-trip time of a trivial query; null when the database is down
     * @param migrationVersion latest successfully applied Flyway migration, when known
     * @param error            short, non-sensitive description when the database is down
     */
    public record DatabaseInfo(Status status, Long latencyMs, String migrationVersion, String error) {
    }
}
