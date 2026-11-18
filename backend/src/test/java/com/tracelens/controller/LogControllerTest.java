package com.tracelens.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tracelens.dto.BulkLogIngestResponse;
import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.LogLevel;
import com.tracelens.dto.PagedResponse;
import com.tracelens.service.LogService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LogController.class)
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogService logService;

    @Test
    void ingestsSingleLogSuccessfully() throws Exception {
        LogEntryDto saved = new LogEntryDto(
                100L, Instant.now(), LogLevel.INFO, "order-service", "production",
                "Order checkout completed", "com.order.Checkout", "main",
                "req-123", "usr-1", "trace-1", "span-1", null, null, null, null, null
        );

        when(logService.ingest(any(LogEntryDto.class))).thenReturn(saved);

        String json = """
            {
                "level": "INFO",
                "serviceName": "order-service",
                "message": "Order checkout completed",
                "requestId": "req-123"
            }
            """;

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.serviceName").value("order-service"))
                .andExpect(jsonPath("$.level").value("INFO"))
                .andExpect(jsonPath("$.message").value("Order checkout completed"));
    }

    @Test
    void rejectsSingleLogWithMissingRequiredFields() throws Exception {
        String invalidJson = """
            {
                "message": ""
            }
            """;

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void ingestsBulkLogsSuccessfully() throws Exception {
        BulkLogIngestResponse response = new BulkLogIngestResponse(
                2, 1, 1, "Successfully ingested 2 log records."
        );

        when(logService.ingestBulk(any())).thenReturn(response);

        String bulkJson = """
            {
                "logs": [
                    { "level": "INFO", "serviceName": "auth-service", "message": "User logged in" },
                    { "level": "ERROR", "serviceName": "payment-service", "message": "Card expired" }
                ]
            }
            """;

        mockMvc.perform(post("/api/v1/logs/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ingestedCount").value(2))
                .andExpect(jsonPath("$.errorCount").value(1))
                .andExpect(jsonPath("$.createdIncidentsCount").value(1));
    }

    @Test
    void searchesLogsWithPagination() throws Exception {
        LogEntryDto logItem = new LogEntryDto(
                1L, Instant.now(), LogLevel.WARN, "payment-service", "production",
                "Payment slow warning", null, null, "req-1", null, null, null, null, null, null, null, null
        );

        PagedResponse<LogEntryDto> paged = new PagedResponse<>(List.of(logItem), 1, 50, 0, false);
        when(logService.search(any(), any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(paged);

        mockMvc.perform(get("/api/v1/logs?service=payment-service&level=WARN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].serviceName").value("payment-service"))
                .andExpect(jsonPath("$.items[0].level").value("WARN"));
    }

    @Test
    void returns404WhenLogIdNotFound() throws Exception {
        when(logService.findById(9999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/logs/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
