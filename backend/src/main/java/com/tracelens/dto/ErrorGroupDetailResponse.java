package com.tracelens.dto;

import java.util.List;

public record ErrorGroupDetailResponse(
        ErrorGroupResponse group,
        List<LogEntryDto> sampleLogs) {
}
