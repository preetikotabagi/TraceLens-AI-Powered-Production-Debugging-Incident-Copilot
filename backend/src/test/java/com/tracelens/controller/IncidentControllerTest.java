package com.tracelens.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tracelens.dto.AiInvestigationResponse;
import com.tracelens.dto.IncidentDetailResponse;
import com.tracelens.dto.IncidentResponse;
import com.tracelens.dto.IncidentStatus;
import com.tracelens.dto.PagedResponse;
import com.tracelens.dto.RecommendationDto;
import com.tracelens.dto.RootCauseHypothesisDto;
import com.tracelens.dto.Severity;
import com.tracelens.dto.SuspiciousEventDto;
import com.tracelens.service.IncidentService;
import com.tracelens.service.LlmInvestigationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentService incidentService;

    @MockitoBean
    private LlmInvestigationService investigationService;

    @Test
    void listsIncidents() throws Exception {
        IncidentResponse incident = new IncidentResponse(
                1L, "[CRITICAL] Deadlock", "order-service",
                Severity.CRITICAL, IncidentStatus.OPEN, "fp_123",
                "CRITICAL_ERROR_SIGNATURE", 5, Instant.now(), Instant.now(),
                "Summary", Instant.now(), Instant.now()
        );

        PagedResponse<IncidentResponse> paged = new PagedResponse<>(List.of(incident), 1, 50, 0, false);
        when(incidentService.listIncidents(any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("[CRITICAL] Deadlock"))
                .andExpect(jsonPath("$.items[0].severity").value("CRITICAL"));
    }

    @Test
    void getsIncidentDetail() throws Exception {
        IncidentResponse incident = new IncidentResponse(
                2L, "[HIGH] Stripe Gateway Timeout", "payment-service",
                Severity.HIGH, IncidentStatus.OPEN, "fp_pay",
                "HIGH_ERROR_FREQUENCY_SPIKE", 12, Instant.now(), Instant.now(),
                "Summary", Instant.now(), Instant.now()
        );

        IncidentDetailResponse detail = new IncidentDetailResponse(incident, null, List.of());
        when(incidentService.getIncidentDetail(2L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/incidents/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incident.id").value(2))
                .andExpect(jsonPath("$.incident.serviceName").value("payment-service"));
    }

    @Test
    void updatesIncidentStatus() throws Exception {
        IncidentResponse updated = new IncidentResponse(
                2L, "[HIGH] Stripe Gateway Timeout", "payment-service",
                Severity.HIGH, IncidentStatus.RESOLVED, "fp_pay",
                "HIGH_ERROR_FREQUENCY_SPIKE", 12, Instant.now(), Instant.now(),
                "Summary", Instant.now(), Instant.now()
        );

        when(incidentService.updateStatus(eq(2L), eq(IncidentStatus.RESOLVED))).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/incidents/2/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void triggersAiInvestigation() throws Exception {
        AiInvestigationResponse investigation = new AiInvestigationResponse(
                50L, 2L, "gpt-4o-mini",
                "Stripe connection timed out after 5000ms",
                List.of(new SuspiciousEventDto(Instant.now(), "payment-service", "504 Gateway Timeout", "Payment endpoint exceeded timeout")),
                List.of(new RootCauseHypothesisDto("Downstream Stripe Latency", "HIGH", List.of("Log timeout"), "Deduction")),
                List.of(new RecommendationDto("Increase timeout", "IMMEDIATE", "Prevents failure")),
                "{}", Instant.now()
        );

        when(investigationService.investigateIncident(eq(2L), any())).thenReturn(investigation);

        mockMvc.perform(post("/api/v1/incidents/2/investigate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Checked network status\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.incidentId").value(2))
                .andExpect(jsonPath("$.summary").value("Stripe connection timed out after 5000ms"))
                .andExpect(jsonPath("$.hypotheses[0].confidence").value("HIGH"));
    }
}
