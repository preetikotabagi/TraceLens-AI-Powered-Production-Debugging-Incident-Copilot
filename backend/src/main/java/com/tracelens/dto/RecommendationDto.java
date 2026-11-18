package com.tracelens.dto;

public record RecommendationDto(
        String action,
        String priority,
        String rationale) {
}
