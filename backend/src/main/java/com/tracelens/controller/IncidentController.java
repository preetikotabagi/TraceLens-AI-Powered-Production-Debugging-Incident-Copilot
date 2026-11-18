package com.tracelens.controller;

import com.tracelens.dto.AiInvestigationRequest;
import com.tracelens.dto.AiInvestigationResponse;
import com.tracelens.dto.IncidentDetailResponse;
import com.tracelens.dto.IncidentResponse;
import com.tracelens.dto.IncidentStatus;
import com.tracelens.dto.IncidentStatusUpdateRequest;
import com.tracelens.dto.PagedResponse;
import com.tracelens.dto.Severity;
import com.tracelens.service.IncidentService;
import com.tracelens.service.LlmInvestigationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final LlmInvestigationService investigationService;

    public IncidentController(IncidentService incidentService,
                              LlmInvestigationService investigationService) {
        this.incidentService = incidentService;
        this.investigationService = investigationService;
    }

    @GetMapping
    public PagedResponse<IncidentResponse> listIncidents(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return incidentService.listIncidents(service, severity, status, limit, offset);
    }

    @GetMapping("/{id}")
    public IncidentDetailResponse getIncident(@PathVariable Long id) {
        return incidentService.getIncidentDetail(id);
    }

    @PatchMapping("/{id}/status")
    public IncidentResponse updateStatus(@PathVariable Long id,
                                         @Valid @RequestBody IncidentStatusUpdateRequest request) {
        return incidentService.updateStatus(id, request.status());
    }

    @PostMapping("/{id}/investigate")
    public AiInvestigationResponse investigate(@PathVariable Long id,
                                               @RequestBody(required = false) AiInvestigationRequest request) {
        return investigationService.investigateIncident(id, request != null ? request : new AiInvestigationRequest(null, null));
    }

    @GetMapping("/{id}/investigations")
    public List<AiInvestigationResponse> getInvestigations(@PathVariable Long id) {
        return investigationService.getInvestigationsForIncident(id);
    }
}
