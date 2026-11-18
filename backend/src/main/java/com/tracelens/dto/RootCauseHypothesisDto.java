package com.tracelens.dto;

import java.util.List;

public record RootCauseHypothesisDto(
        String hypothesis,
        String confidence,
        List<String> supportingEvidence,
        String reasoning) {
}
