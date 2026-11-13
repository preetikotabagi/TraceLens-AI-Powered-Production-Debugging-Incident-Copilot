package com.tracelens.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tracelens.dto.HealthResponse;
import com.tracelens.dto.HealthResponse.ApplicationInfo;
import com.tracelens.dto.HealthResponse.DatabaseInfo;
import com.tracelens.dto.HealthResponse.Status;
import com.tracelens.service.HealthService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthService healthService;

    private static final ApplicationInfo APP = new ApplicationInfo("TraceLens", "0.1.0");

    @Test
    void returns200AndUpWhenDatabaseIsReachable() throws Exception {
        when(healthService.check()).thenReturn(new HealthResponse(
                Status.UP, APP, new DatabaseInfo(Status.UP, 4L, "1", null), Instant.now()));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application.name").value("TraceLens"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.database.migrationVersion").value("1"))
                .andExpect(jsonPath("$.database.error").doesNotExist());
    }

    @Test
    void returns503AndDownWhenDatabaseIsUnreachable() throws Exception {
        when(healthService.check()).thenReturn(new HealthResponse(
                Status.DOWN, APP,
                new DatabaseInfo(Status.DOWN, null, null, "Database connection failed"), Instant.now()));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.database.status").value("DOWN"))
                .andExpect(jsonPath("$.database.error").value("Database connection failed"));
    }

    @Test
    void echoesIncomingRequestIdHeader() throws Exception {
        when(healthService.check()).thenReturn(new HealthResponse(
                Status.UP, APP, new DatabaseInfo(Status.UP, 1L, "1", null), Instant.now()));

        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "req-abc_123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-abc_123"));
    }

    @Test
    void replacesUnsafeRequestIdWithGeneratedOne() throws Exception {
        when(healthService.check()).thenReturn(new HealthResponse(
                Status.UP, APP, new DatabaseInfo(Status.UP, 1L, "1", null), Instant.now()));

        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "bad id with spaces!"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(result -> {
                    String id = result.getResponse().getHeader("X-Request-Id");
                    if (id == null || id.contains(" ") || id.contains("!")) {
                        throw new AssertionError("Unsafe request id was echoed: " + id);
                    }
                });
    }
}
