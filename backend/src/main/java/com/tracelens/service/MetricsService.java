package com.tracelens.service;

import com.tracelens.dto.IncidentResponse;
import com.tracelens.dto.OverviewMetricsResponse;
import com.tracelens.dto.ServiceHealthDto;
import com.tracelens.repository.IncidentRepository;
import com.tracelens.repository.LogRepository;
import com.tracelens.repository.ServiceHealthRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final LogRepository logRepository;
    private final IncidentRepository incidentRepository;
    private final ServiceHealthRepository serviceHealthRepository;

    public MetricsService(LogRepository logRepository,
                          IncidentRepository incidentRepository,
                          ServiceHealthRepository serviceHealthRepository) {
        this.logRepository = logRepository;
        this.incidentRepository = incidentRepository;
        this.serviceHealthRepository = serviceHealthRepository;
    }

    public OverviewMetricsResponse getOverviewMetrics() {
        long totalLogs = logRepository.countTotalLogs();
        long errorCount = logRepository.countTotalErrors();
        double errorRate = totalLogs > 0 ? (double) errorCount / totalLogs * 100.0 : 0.0;
        errorRate = Math.round(errorRate * 100.0) / 100.0;

        long activeIncidents = incidentRepository.countActiveIncidents();
        Map<String, Long> severityDist = incidentRepository.getSeverityDistribution();
        List<ServiceHealthDto> serviceHealth = serviceHealthRepository.getServiceHealthStats();
        List<IncidentResponse> recentIncidents = incidentRepository.findRecent(5);

        return new OverviewMetricsResponse(
                totalLogs,
                errorCount,
                errorRate,
                activeIncidents,
                severityDist,
                serviceHealth,
                recentIncidents
        );
    }

    public List<ServiceHealthDto> getServiceHealth() {
        return serviceHealthRepository.getServiceHealthStats();
    }
}
