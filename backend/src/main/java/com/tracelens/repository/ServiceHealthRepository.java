package com.tracelens.repository;

import com.tracelens.dto.ServiceHealthDto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceHealthRepository {

    private final JdbcTemplate jdbcTemplate;

    public ServiceHealthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ServiceHealthDto> getServiceHealthStats() {
        String sql = """
            SELECT
                service_name,
                COUNT(*) as total_logs,
                COUNT(CASE WHEN level IN ('ERROR', 'FATAL') THEN 1 END) as error_count,
                MAX(timestamp) as last_seen
            FROM logs
            GROUP BY service_name
            ORDER BY service_name ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String serviceName = rs.getString("service_name");
            long total = rs.getLong("total_logs");
            long errors = rs.getLong("error_count");
            Timestamp lastSeenTs = rs.getTimestamp("last_seen");
            Instant lastSeen = lastSeenTs != null ? lastSeenTs.toInstant() : null;

            double errorRate = total > 0 ? (double) errors / total * 100.0 : 0.0;
            // Round to 2 decimal places
            errorRate = Math.round(errorRate * 100.0) / 100.0;

            String status;
            if (errorRate > 25.0) {
                status = "DOWN";
            } else if (errorRate > 5.0 || errors >= 5) {
                status = "DEGRADED";
            } else {
                status = "UP";
            }

            return new ServiceHealthDto(serviceName, total, errors, errorRate, status, lastSeen);
        });
    }
}
