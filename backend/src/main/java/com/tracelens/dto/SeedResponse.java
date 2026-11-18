package com.tracelens.dto;

public record SeedResponse(
        int seededLogs,
        int seededErrorGroups,
        int seededIncidents,
        String message) {
}
