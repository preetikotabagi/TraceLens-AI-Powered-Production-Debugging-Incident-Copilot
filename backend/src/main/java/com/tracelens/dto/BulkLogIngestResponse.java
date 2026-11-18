package com.tracelens.dto;

public record BulkLogIngestResponse(
        int ingestedCount,
        int errorCount,
        int createdIncidentsCount,
        String message) {
}
