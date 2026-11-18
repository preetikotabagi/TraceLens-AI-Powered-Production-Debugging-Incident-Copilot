package com.tracelens.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tracelens.dto.OverviewMetricsResponse;
import com.tracelens.dto.ServiceHealthDto;
import com.tracelens.service.MetricsService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MetricsController.class)
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MetricsService metricsService;

    @Test
    void returnsOverviewMetrics() throws Exception {
        OverviewMetricsResponse overview = new OverviewMetricsResponse(
                1250L, 45L, 3.6, 2L,
                Map.of("CRITICAL", 1L, "HIGH", 1L),
                List.of(new ServiceHealthDto("payment-service", 300, 25, 8.33, "DEGRADED", Instant.now())),
                List.of()
        );

        when(metricsService.getOverviewMetrics()).thenReturn(overview);

        mockMvc.perform(get("/api/v1/metrics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLogs").value(1250))
                .andExpect(jsonPath("$.errorCount").value(45))
                .andExpect(jsonPath("$.errorRate").value(3.6))
                .andExpect(jsonPath("$.activeIncidentsCount").value(2))
                .andExpect(jsonPath("$.serviceHealth[0].serviceName").value("payment-service"));
    }
}
