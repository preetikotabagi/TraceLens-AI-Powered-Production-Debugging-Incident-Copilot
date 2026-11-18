package com.tracelens.service;

import com.tracelens.dto.ErrorGroupResponse;
import com.tracelens.dto.IncidentDetailResponse;
import com.tracelens.dto.IncidentResponse;
import com.tracelens.dto.IncidentStatus;
import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.PagedResponse;
import com.tracelens.dto.Severity;
import com.tracelens.exception.ResourceNotFoundException;
import com.tracelens.repository.ErrorGroupRepository;
import com.tracelens.repository.IncidentRepository;
import com.tracelens.repository.LogRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final ErrorGroupRepository errorGroupRepository;
    private final LogRepository logRepository;

    public IncidentService(IncidentRepository incidentRepository,
                           ErrorGroupRepository errorGroupRepository,
                           LogRepository logRepository) {
        this.incidentRepository = incidentRepository;
        this.errorGroupRepository = errorGroupRepository;
        this.logRepository = logRepository;
    }

    public PagedResponse<IncidentResponse> listIncidents(String serviceName, Severity severity,
                                                         IncidentStatus status, int limit, int offset) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        int boundedOffset = Math.max(0, offset);

        List<IncidentResponse> items = incidentRepository.findAll(serviceName, severity, status, boundedLimit, boundedOffset);
        long total = incidentRepository.countAll(serviceName, severity, status);
        boolean hasMore = (boundedOffset + items.size()) < total;

        return new PagedResponse<>(items, total, boundedLimit, boundedOffset, hasMore);
    }

    public IncidentDetailResponse getIncidentDetail(Long id) {
        IncidentResponse incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with ID: " + id));

        ErrorGroupResponse errorGroup = null;
        if (incident.fingerprint() != null) {
            errorGroup = errorGroupRepository.findByFingerprint(incident.fingerprint()).orElse(null);
        }

        List<LogEntryDto> logs = logRepository.findByIncidentId(id, 50);
        if (logs.isEmpty() && incident.fingerprint() != null) {
            logs = logRepository.findByFingerprint(incident.fingerprint(), 50);
        }

        return new IncidentDetailResponse(incident, errorGroup, logs);
    }

    @Transactional
    public IncidentResponse updateStatus(Long id, IncidentStatus newStatus) {
        boolean updated = incidentRepository.updateStatus(id, newStatus);
        if (!updated) {
            throw new ResourceNotFoundException("Incident not found with ID: " + id);
        }
        return incidentRepository.findById(id).orElseThrow();
    }
}
