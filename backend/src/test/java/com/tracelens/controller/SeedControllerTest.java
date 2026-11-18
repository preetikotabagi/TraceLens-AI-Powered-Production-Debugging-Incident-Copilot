package com.tracelens.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tracelens.dto.SeedResponse;
import com.tracelens.service.SeedDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SeedController.class)
class SeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeedDataService seedDataService;

    @Test
    void seedsRealisticLogsSuccessfully() throws Exception {
        SeedResponse response = new SeedResponse(
                1050, 6, 3, "Successfully seeded 1050 realistic logs across 5 services."
        );

        when(seedDataService.seedRealisticData()).thenReturn(response);

        mockMvc.perform(post("/api/v1/seed"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seededLogs").value(1050))
                .andExpect(jsonPath("$.seededErrorGroups").value(6))
                .andExpect(jsonPath("$.seededIncidents").value(3))
                .andExpect(jsonPath("$.message").value("Successfully seeded 1050 realistic logs across 5 services."));
    }
}
