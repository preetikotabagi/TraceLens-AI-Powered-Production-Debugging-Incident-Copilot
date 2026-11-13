package com.tracelens.dto;

/**
 * Response for a demo scenario that completes successfully (currently only the slow request).
 */
public record DemoSimulationResponse(ScenarioType scenario, long elapsedMs, String message) {
}
