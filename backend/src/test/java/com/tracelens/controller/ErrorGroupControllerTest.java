package com.tracelens.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tracelens.dto.ErrorGroupDetailResponse;
import com.tracelens.dto.ErrorGroupResponse;
import com.tracelens.dto.PagedResponse;
import com.tracelens.service.ErrorGroupService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ErrorGroupController.class)
class ErrorGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ErrorGroupService errorGroupService;

    @Test
    void listsErrorGroups() throws Exception {
        ErrorGroupResponse group = new ErrorGroupResponse(
                "fp_deadlock99", "PSQLException", "order-service",
                "PSQLException: ERROR: deadlock detected", "stack trace",
                14, Instant.now(), Instant.now(), Instant.now(), Instant.now()
        );

        PagedResponse<ErrorGroupResponse> paged = new PagedResponse<>(List.of(group), 1, 50, 0, false);
        when(errorGroupService.listGroups(any(), any(Integer.class), any(Integer.class))).thenReturn(paged);

        mockMvc.perform(get("/api/v1/error-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].fingerprint").value("fp_deadlock99"))
                .andExpect(jsonPath("$.items[0].occurrenceCount").value(14));
    }

    @Test
    void getsErrorGroupDetail() throws Exception {
        ErrorGroupResponse group = new ErrorGroupResponse(
                "fp_deadlock99", "PSQLException", "order-service",
                "PSQLException: ERROR: deadlock detected", "stack trace",
                14, Instant.now(), Instant.now(), Instant.now(), Instant.now()
        );

        ErrorGroupDetailResponse detail = new ErrorGroupDetailResponse(group, List.of());
        when(errorGroupService.getGroupDetail("fp_deadlock99")).thenReturn(detail);

        mockMvc.perform(get("/api/v1/error-groups/fp_deadlock99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group.fingerprint").value("fp_deadlock99"))
                .andExpect(jsonPath("$.group.errorType").value("PSQLException"));
    }
}
