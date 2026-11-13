package com.tracelens.service;

import com.tracelens.config.TraceLensProperties;
import com.tracelens.dto.DemoSimulationResponse;
import com.tracelens.dto.ScenarioType;
import com.tracelens.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;

/**
 * DEVELOPMENT / DEMO ONLY.
 *
 * <p>Produces a few controlled errors so that later TraceLens features (log ingestion, incident
 * detection) have realistic failures to observe. This bean only exists when
 * {@code tracelens.demo.enabled=true}, and {@code DemoSafetyGuard} blocks startup if that is combined
 * with the {@code prod} profile.
 *
 * <p>Nothing here touches real data or damages the database: the "database failure" is a thrown
 * exception, and the "slow request" sleeps for a bounded time.
 */
@Service
@ConditionalOnProperty(prefix = "tracelens.demo", name = "enabled", havingValue = "true")
public class DemoErrorService {

    static final int DEFAULT_DELAY_MS = 3000;

    private static final Logger log = LoggerFactory.getLogger(DemoErrorService.class);

    private final TraceLensProperties properties;

    public DemoErrorService(TraceLensProperties properties) {
        this.properties = properties;
    }

    public DemoSimulationResponse simulate(ScenarioType scenario, Integer delayMs) {
        log.info("Running demo scenario {}", scenario);
        return switch (scenario) {
            case DATABASE_FAILURE -> throw new DataAccessResourceFailureException(
                    "Simulated (demo): connection to the database was lost");
            case INVALID_REQUEST -> throw new BadRequestException(
                    "DEMO_INVALID_REQUEST", "Simulated (demo): the request was deliberately invalid.");
            case SLOW_REQUEST -> simulateSlowRequest(delayMs);
        };
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
