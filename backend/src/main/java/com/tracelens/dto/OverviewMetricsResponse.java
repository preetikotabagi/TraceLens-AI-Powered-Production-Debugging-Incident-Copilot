package com.tracelens.dto;

import java.util.List;
import java.util.Map;

public record OverviewMetricsResponse(
        long totalLogs,
        long errorCount,
        double errorRate,
        long activeIncidentsCount,
        Map<String, Long> severityDistribution,
        List<ServiceHealthDto> serviceHealth,
        List<IncidentResponse> recentIncidents) {
}
