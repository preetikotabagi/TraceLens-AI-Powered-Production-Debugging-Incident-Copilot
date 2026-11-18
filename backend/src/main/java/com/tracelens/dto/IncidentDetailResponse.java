package com.tracelens.dto;

import java.util.List;

public record IncidentDetailResponse(
        IncidentResponse incident,
        ErrorGroupResponse errorGroup,
        List<LogEntryDto> associatedLogs) {
}
