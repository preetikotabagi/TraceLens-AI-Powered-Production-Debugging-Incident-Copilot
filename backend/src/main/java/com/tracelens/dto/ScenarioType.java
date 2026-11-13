package com.tracelens.dto;

/**
 * Controlled failure scenarios of the development-only demo service.
 */
public enum ScenarioType {
    /** Simulates the database becoming unreachable (results in HTTP 503). */
    DATABASE_FAILURE,
    /** Simulates a client sending an invalid request (results in HTTP 400). */
    INVALID_REQUEST,
    /** Simulates a slow endpoint by sleeping for a bounded time (results in HTTP 200). */
    SLOW_REQUEST
}
