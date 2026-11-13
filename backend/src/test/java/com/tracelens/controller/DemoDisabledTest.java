package com.tracelens.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tracelens.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With demo mode disabled (the default), the demo endpoint must not exist at all.
 */
@WebMvcTest(controllers = {HealthController.class, DemoController.class},
        properties = "tracelens.demo.enabled=false")
class DemoDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthService healthService;

    @Test
    void demoEndpointDoesNotExistWhenDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/demo/simulate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void demoEndpointCannotBeInvokedWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/demo/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"DATABASE_FAILURE\"}"))
                .andExpect(status().is4xxClientError());
    }
}
