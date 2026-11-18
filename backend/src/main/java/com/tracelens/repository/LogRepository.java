package com.tracelens.repository;

import com.tracelens.dto.LogEntryDto;
import com.tracelens.dto.LogLevel;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class LogRepository {

    private final JdbcTemplate jdbcTemplate;

    public LogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<LogEntryDto> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    private static LogEntryDto mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("timestamp");
        Long incidentId = rs.getObject("incident_id") != null ? rs.getLong("incident_id") : null;

        return new LogEntryDto(
                rs.getLong("id"),
                ts != null ? ts.toInstant() : null,
                LogLevel.valueOf(rs.getString("level")),
                rs.getString("service_name"),
                rs.getString("environment"),
                rs.getString("message"),
                rs.getString("logger"),
                rs.getString("thread_name"),
                rs.getString("request_id"),
                rs.getString("user_id"),
                rs.getString("trace_id"),
                rs.getString("span_id"),
                rs.getString("error_type"),
                rs.getString("stack_trace"),
                rs.getString("fingerprint"),
                incidentId,
                rs.getString("metadata")
        );
    }

    public LogEntryDto save(LogEntryDto log) {
        String sql = """
            INSERT INTO logs (
                timestamp, level, service_name, environment, message, logger,
                thread_name, request_id, user_id, trace_id, span_id, error_type,
                stack_trace, fingerprint, incident_id, metadata, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """;

        Instant ts = log.timestamp() != null ? log.timestamp() : Instant.now();
        String env = log.environment() != null ? log.environment() : "production";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setTimestamp(1, Timestamp.from(ts));
            ps.setString(2, log.level().name());
            ps.setString(3, log.serviceName());
            ps.setString(4, env);
            ps.setString(5, log.message());
            ps.setString(6, log.logger());
            ps.setString(7, log.threadName());
            ps.setString(8, log.requestId());
            ps.setString(9, log.userId());
            ps.setString(10, log.traceId());
            ps.setString(11, log.spanId());
            ps.setString(12, log.errorType());
            ps.setString(13, log.stackTrace());
            ps.setString(14, log.fingerprint());
            if (log.incidentId() != null) {
                ps.setLong(15, log.incidentId());
            } else {
                ps.setNull(15, java.sql.Types.BIGINT);
            }
            ps.setString(16, log.metadata());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key != null ? key.longValue() : 0L;
        return findById(id).orElse(log);
    }

    public void saveAll(List<LogEntryDto> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO logs (
                timestamp, level, service_name, environment, message, logger,
                thread_name, request_id, user_id, trace_id, span_id, error_type,
                stack_trace, fingerprint, incident_id, metadata, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
            """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LogEntryDto log = logs.get(i);
                Instant ts = log.timestamp() != null ? log.timestamp() : Instant.now();
                String env = log.environment() != null ? log.environment() : "production";

                ps.setTimestamp(1, Timestamp.from(ts));
                ps.setString(2, log.level().name());
                ps.setString(3, log.serviceName());
                ps.setString(4, env);
                ps.setString(5, log.message());
                ps.setString(6, log.logger());
                ps.setString(7, log.threadName());
                ps.setString(8, log.requestId());
                ps.setString(9, log.userId());
                ps.setString(10, log.traceId());
                ps.setString(11, log.spanId());
                ps.setString(12, log.errorType());
                ps.setString(13, log.stackTrace());
                ps.setString(14, log.fingerprint());
                if (log.incidentId() != null) {
                    ps.setLong(15, log.incidentId());
                } else {
                    ps.setNull(15, java.sql.Types.BIGINT);
                }
                ps.setString(16, log.metadata());
            }

            @Override
            public int getBatchSize() {
                return logs.size();
            }
        });
    }

    public Optional<LogEntryDto> findById(Long id) {
        String sql = "SELECT * FROM logs WHERE id = ?";
        List<LogEntryDto> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    public List<LogEntryDto> search(String serviceName, LogLevel level, String query,
                                    String fingerprint, Long incidentId,
                                    Instant startTime, Instant endTime,
                                    int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (serviceName != null && !serviceName.isBlank()) {
            sql.append("AND service_name = ? ");
            params.add(serviceName.trim());
        }
        if (level != null) {
            sql.append("AND level = ? ");
            params.add(level.name());
        }
        if (fingerprint != null && !fingerprint.isBlank()) {
            sql.append("AND fingerprint = ? ");
            params.add(fingerprint.trim());
        }
        if (incidentId != null) {
            sql.append("AND incident_id = ? ");
            params.add(incidentId);
        }
        if (startTime != null) {
            sql.append("AND timestamp >= ? ");
            params.add(Timestamp.from(startTime));
        }
        if (endTime != null) {
            sql.append("AND timestamp <= ? ");
            params.add(Timestamp.from(endTime));
        }
        if (query != null && !query.isBlank()) {
            sql.append("AND (message ILIKE ? OR error_type ILIKE ? OR logger ILIKE ? OR request_id ILIKE ?) ");
            String searchPattern = "%" + query.trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
            params.add(searchPattern);
            params.add(searchPattern);
        }

        sql.append("ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long count(String serviceName, LogLevel level, String query,
                      String fingerprint, Long incidentId,
                      Instant startTime, Instant endTime) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (serviceName != null && !serviceName.isBlank()) {
            sql.append("AND service_name = ? ");
            params.add(serviceName.trim());
        }
        if (level != null) {
            sql.append("AND level = ? ");
            params.add(level.name());
        }
        if (fingerprint != null && !fingerprint.isBlank()) {
            sql.append("AND fingerprint = ? ");
            params.add(fingerprint.trim());
        }
        if (incidentId != null) {
            sql.append("AND incident_id = ? ");
            params.add(incidentId);
        }
        if (startTime != null) {
            sql.append("AND timestamp >= ? ");
            params.add(Timestamp.from(startTime));
        }
        if (endTime != null) {
            sql.append("AND timestamp <= ? ");
            params.add(Timestamp.from(endTime));
        }
        if (query != null && !query.isBlank()) {
            sql.append("AND (message ILIKE ? OR error_type ILIKE ? OR logger ILIKE ? OR request_id ILIKE ?) ");
            String searchPattern = "%" + query.trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
            params.add(searchPattern);
            params.add(searchPattern);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public List<LogEntryDto> findByIncidentId(Long incidentId, int limit) {
        String sql = "SELECT * FROM logs WHERE incident_id = ? ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, incidentId, limit);
    }

    public List<LogEntryDto> findByFingerprint(String fingerprint, int limit) {
        String sql = "SELECT * FROM logs WHERE fingerprint = ? ORDER BY timestamp DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, fingerprint, limit);
    }

    public List<LogEntryDto> findLogsAroundTime(String serviceName, Instant centerTime, int windowMinutes, int limit) {
        Instant start = centerTime.minusSeconds(windowMinutes * 60L);
        Instant end = centerTime.plusSeconds(windowMinutes * 60L);

        String sql = """
            SELECT * FROM logs
            WHERE service_name = ? AND timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp ASC LIMIT ?
            """;
        return jdbcTemplate.query(sql, ROW_MAPPER, serviceName, Timestamp.from(start), Timestamp.from(end), limit);
    }

    public void linkIncidentByFingerprintSince(String fingerprint, Instant since, Long incidentId) {
        String sql = """
            UPDATE logs SET incident_id = ?
            WHERE fingerprint = ? AND timestamp >= ? AND (incident_id IS NULL OR incident_id = ?)
            """;
        jdbcTemplate.update(sql, incidentId, fingerprint, Timestamp.from(since), incidentId);
    }

    public int countErrorsForServiceInWindow(String serviceName, Instant windowStart) {
        String sql = """
            SELECT COUNT(*) FROM logs
            WHERE service_name = ? AND level IN ('ERROR', 'FATAL') AND timestamp >= ?
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, serviceName, Timestamp.from(windowStart));
        return count != null ? count : 0;
    }

    public long countTotalLogs() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM logs", Long.class);
        return count != null ? count : 0L;
    }

    public long countTotalErrors() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM logs WHERE level IN ('ERROR', 'FATAL')", Long.class);
        return count != null ? count : 0L;
    }
}
