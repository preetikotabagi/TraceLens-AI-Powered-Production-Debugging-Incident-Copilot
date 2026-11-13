package com.tracelens.service;

import com.tracelens.config.TraceLensProperties;
import com.tracelens.dto.HealthResponse;
import com.tracelens.dto.HealthResponse.ApplicationInfo;
import com.tracelens.dto.HealthResponse.DatabaseInfo;
import com.tracelens.dto.HealthResponse.Status;
import com.tracelens.repository.DatabaseHealthRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Builds the health report: is the application running, and can it reach its database?
 */
@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final DatabaseHealthRepository repository;
    private final TraceLensProperties properties;

    public HealthService(DatabaseHealthRepository repository, TraceLensProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public HealthResponse check() {
        ApplicationInfo application = new ApplicationInfo(
                properties.app().name(), properties.app().version());

        DatabaseInfo database;
        long start = System.nanoTime();
        try {
            repository.ping();
            long latencyMs = (System.nanoTime() - start) / 1_000_000;
            database = new DatabaseInfo(Status.UP, latencyMs, readMigrationVersion(), null);
        } catch (DataAccessException ex) {
            // Full detail goes to the log only; the response stays generic.
            log.warn("Database health check failed: {}", ex.getMessage());
            database = new DatabaseInfo(Status.DOWN, null, null, "Database connection failed");
        }

        Status overall = database.status() == Status.UP ? Status.UP : Status.DOWN;
        return new HealthResponse(overall, application, database, Instant.now());
    }

    private String readMigrationVersion() {
        try {
            return repository.latestMigrationVersion().orElse(null);
        } catch (DataAccessException ex) {
            log.debug("Could not read migration version: {}", ex.getMessage());
            return null;
        }
    }
}
