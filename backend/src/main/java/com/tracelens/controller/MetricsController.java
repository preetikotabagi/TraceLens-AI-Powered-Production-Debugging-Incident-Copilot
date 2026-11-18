package com.tracelens.controller;

import com.tracelens.dto.OverviewMetricsResponse;
import com.tracelens.service.MetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/overview")
    public OverviewMetricsResponse getOverviewMetrics() {
        return metricsService.getOverviewMetrics();
    }
}
