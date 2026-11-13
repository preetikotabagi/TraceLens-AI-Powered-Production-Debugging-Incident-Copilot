package com.tracelens.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tracelens.service.DemoErrorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the demo endpoint together with the real {@link DemoErrorService} and the global
 * exception handler, with demo mode enabled.
 */
@WebMvcTest(controllers = DemoController.class, properties = "tracelens.demo.enabled=true")
@Import(DemoErrorService.class)
class DemoControllerTest {

    private static final String URL = "/api/v1/demo/simulate";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidRequestScenarioReturns400WithErrorBody() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-Request-Id", "test-req-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"INVALID_REQUEST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("DEMO_INVALID_REQUEST"))
                .andExpect(jsonPath("$.path").value(URL))
                .andExpect(jsonPath("$.requestId").value("test-req-1"));
    }

    @Test
    void databaseFailureScenarioReturns503WithoutLeakingInternals() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"DATABASE_FAILURE\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(not(containsString("Simulated"))))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void slowRequestScenarioReturns200WithElapsedTime() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"SLOW_REQUEST\",\"delayMs\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("SLOW_REQUEST"))
                .andExpect(jsonPath("$.elapsedMs").isNumber());
    }

    @Test
    void slowRequestAboveMaximumDelayReturns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"SLOW_REQUEST\",\"delayMs\":10001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DEMO_DELAY_TOO_LARGE"));
    }

    @Test
    void missingScenarioFailsValidationWithFieldDetails() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("scenario"));
    }

    @Test
    void negativeDelayFailsValidation() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"SLOW_REQUEST\",\"delayMs\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("delayMs"));
    }

    @Test
    void unknownScenarioValueReturnsMalformedRequest() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"NOT_A_SCENARIO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unreadableJsonReturnsMalformedRequest() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void wrongHttpMethodReturns405() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
