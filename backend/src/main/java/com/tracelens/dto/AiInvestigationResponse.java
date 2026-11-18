package com.tracelens.dto;

import java.time.Instant;
import java.util.List;

public record AiInvestigationResponse(
        Long id,
        Long incidentId,
        String model,
        String summary,
        List<SuspiciousEventDto> suspiciousSequence,
        List<RootCauseHypothesisDto> hypotheses,
        List<RecommendationDto> recommendations,
        String rawResponse,
        Instant createdAt) {
}
