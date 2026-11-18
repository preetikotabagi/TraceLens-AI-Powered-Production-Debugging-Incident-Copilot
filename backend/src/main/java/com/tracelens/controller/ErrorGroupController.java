package com.tracelens.controller;

import com.tracelens.dto.ErrorGroupDetailResponse;
import com.tracelens.dto.ErrorGroupResponse;
import com.tracelens.dto.PagedResponse;
import com.tracelens.service.ErrorGroupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/error-groups")
public class ErrorGroupController {

    private final ErrorGroupService errorGroupService;

    public ErrorGroupController(ErrorGroupService errorGroupService) {
        this.errorGroupService = errorGroupService;
    }

    @GetMapping
    public PagedResponse<ErrorGroupResponse> listErrorGroups(
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return errorGroupService.listGroups(service, limit, offset);
    }

    @GetMapping("/{fingerprint}")
    public ErrorGroupDetailResponse getErrorGroup(@PathVariable String fingerprint) {
        return errorGroupService.getGroupDetail(fingerprint);
    }
}
