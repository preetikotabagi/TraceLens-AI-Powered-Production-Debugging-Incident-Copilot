package com.tracelens.controller;

import com.tracelens.dto.DemoSimulationRequest;
import com.tracelens.dto.DemoSimulationResponse;
import com.tracelens.service.DemoErrorService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEVELOPMENT / DEMO ONLY. Lets a developer trigger controlled errors on demand.
 *
 * <p>The controller is only registered when {@code tracelens.demo.enabled=true}. When disabled
 * (the default, and mandatory in production) these URLs simply do not exist and return 404.
 */
@RestController
@RequestMapping("/api/v1/demo")
@ConditionalOnProperty(prefix = "tracelens.demo", name = "enabled", havingValue = "true")
public class DemoController {

    private final DemoErrorService demoErrorService;

    public DemoController(DemoErrorService demoErrorService) {
        this.demoErrorService = demoErrorService;
    }

    @PostMapping("/simulate")
    public DemoSimulationResponse simulate(@Valid @RequestBody DemoSimulationRequest request) {
        return demoErrorService.simulate(request.scenario(), request.delayMs());
    }
}
