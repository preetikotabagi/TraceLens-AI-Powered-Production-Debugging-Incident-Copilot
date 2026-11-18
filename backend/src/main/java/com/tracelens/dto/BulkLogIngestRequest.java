package com.tracelens.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkLogIngestRequest(
        @NotEmpty(message = "logs list cannot be empty")
        List<@Valid LogEntryDto> logs) {
}
