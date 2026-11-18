package com.tracelens.service;

import com.tracelens.config.TraceLensProperties;
import com.tracelens.dto.DemoSimulationResponse;
import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.LogLevel;
import com.tracelens.dto.ScenarioType;
import com.tracelens.exception.BadRequestException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;

/**
 * DEVELOPMENT / DEMO ONLY.
 * Produces controlled errors and ingests corresponding logs so that the
 * debugging
 * platform has live failures to inspect.
 */
@Service
@ConditionalOnProperty(prefix = "tracelens.demo", name = "enabled", havingValue = "true")
public class DemoErrorService {

    static final int DEFAULT_DELAY_MS = 3000;

    private static final Logger log = LoggerFactory.getLogger(DemoErrorService.class);

    private final TraceLensProperties properties;
    private final LogService logService;

    public DemoErrorService(TraceLensProperties properties) {
        this(properties, null);
    }

    @Autowired
    public DemoErrorService(TraceLensProperties properties, @Autowired(required = false) LogService logService) {
        this.properties = properties;
        this.logService = logService;
    }

    public DemoSimulationResponse simulate(ScenarioType scenario, Integer delayMs) {
        log.info("Running demo scenario {}", scenario);
        String mdcReqId = org.slf4j.MDC.get(com.tracelens.web.RequestIdFilter.MDC_KEY);
        String reqId = (mdcReqId != null && !mdcReqId.isBlank()) ? mdcReqId
                : "demo-" + UUID.randomUUID().toString().substring(0, 8);

        return switch (scenario) {
            case DATABASE_FAILURE -> {
                recordSimulatedLog(
                        LogLevel.ERROR,
                        "order-service",
                        "DataAccessResourceFailureException: Simulated (demo): connection to the database was lost",
                        "DataAccessResourceFailureException",
                        "org.springframework.dao.DataAccessResourceFailureException: Simulated (demo)\n\tat com.tracelens.service.DemoErrorService.simulate(DemoErrorService.java:42)",
                        reqId);
                throw new DataAccessResourceFailureException(
                        "Simulated (demo): connection to the database was lost");
            }
            case INVALID_REQUEST -> {
                recordSimulatedLog(
                        LogLevel.WARN,
                        "api-gateway",
                        "BadRequestException: Simulated (demo): the request was deliberately invalid.",
                        "BadRequestException",
                        null,
                        reqId);
                throw new BadRequestException(
                        "DEMO_INVALID_REQUEST", "Simulated (demo): the request was deliberately invalid.");
            }
            case SLOW_REQUEST -> {
                DemoSimulationResponse resp = simulateSlowRequest(delayMs);
                recordSimulatedLog(
                        LogLevel.WARN,
                        "inventory-service",
                        String.format("Slow request detected: elapsed %d ms", resp.elapsedMs()),
                        "SlowRequestWarning",
                        null,
                        reqId);
                yield resp;
            }
        };
    }

    private void recordSimulatedLog(LogLevel level, String service, String message,
            String errorType, String stack, String reqId) {
        if (logService == null) {
            return;
        }
        try {
            logService.ingest(new LogEntryDto(
                    null,
                    Instant.now(),
                    level,
                    service,
                    "development",
                    message,
                    "com.tracelens.demo.DemoSimulator",
                    Thread.currentThread().getName(),
                    reqId,
                    "demo-user",
                    "trace-" + reqId,
                    "span-1",
                    errorType,
                    stack,
                    null,
                    null,
                    "{\"demo\":true}"));
        } catch (Exception ex) {
            log.debug("Could not record demo log to database (expected if db is down): {}", ex.getMessage());
        }
    }

    private DemoSimulationResponse simulateSlowRequest(Integer requestedDelayMs) {
        int delay = requestedDelayMs == null ? DEFAULT_DELAY_MS : requestedDelayMs;
        int max = properties.demo().maxDelayMs();
        if (delay > max) {
            throw new BadRequestException("DEMO_DELAY_TOO_LARGE",
                    "delayMs must not exceed " + max + " ms.");
        }

        long start = System.nanoTime();
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Demo slow request interrupted");
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        log.warn("Demo slow request completed after {} ms", elapsedMs);
        return new DemoSimulationResponse(ScenarioType.SLOW_REQUEST, elapsedMs,
                "Simulated (demo): slow request finished.");
    }
}
