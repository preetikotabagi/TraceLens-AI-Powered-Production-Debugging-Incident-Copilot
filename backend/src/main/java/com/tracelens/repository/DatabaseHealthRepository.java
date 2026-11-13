package com.tracelens.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Data access used by the health check. Talks to PostgreSQL through plain JDBC.
 */
@Repository
public class DatabaseHealthRepository {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Executes a trivial query to prove a connection can be obtained and used.
     *
     * @throws org.springframework.dao.DataAccessException if the database cannot be reached
     */
    public void ping() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    /**
     * @return the version of the latest successfully applied Flyway migration, if any
     */
    public Optional<String> latestMigrationVersion() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL "
                        + "ORDER BY installed_rank DESC LIMIT 1",
                String.class);
        return versions.stream().findFirst();
    }
}
