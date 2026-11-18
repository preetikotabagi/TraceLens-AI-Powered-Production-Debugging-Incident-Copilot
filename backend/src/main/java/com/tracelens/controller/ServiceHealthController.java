package com.tracelens.controller;

import com.tracelens.dto.ServiceHealthDto;
import com.tracelens.service.MetricsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceHealthController {

    private final MetricsService metricsService;

    public ServiceHealthController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/health")
    public List<ServiceHealthDto> getServiceHealth() {
        return metricsService.getServiceHealth();
    }
}
