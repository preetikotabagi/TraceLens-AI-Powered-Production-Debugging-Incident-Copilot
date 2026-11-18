package com.tracelens.dto;

import jakarta.validation.constraints.NotNull;

public record IncidentStatusUpdateRequest(
        @NotNull(message = "status is required")
        IncidentStatus status) {
}
