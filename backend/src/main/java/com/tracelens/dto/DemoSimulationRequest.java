package com.tracelens.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body of {@code POST /api/v1/demo/simulate} (development only).
 *
 * @param scenario which controlled failure to produce
 * @param delayMs  only used by {@link ScenarioType#SLOW_REQUEST}; defaults to 3000 ms.
 *                 The upper bound is enforced by the service from configuration.
 */
public record DemoSimulationRequest(
        @NotNull(message = "scenario is required") ScenarioType scenario,
        @Min(value = 0, message = "delayMs must not be negative") Integer delayMs) {
}
