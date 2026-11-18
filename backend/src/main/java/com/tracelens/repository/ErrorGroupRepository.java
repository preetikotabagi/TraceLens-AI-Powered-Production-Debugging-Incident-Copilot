package com.tracelens.repository;

import com.tracelens.dto.ErrorGroupResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ErrorGroupRepository {

    private final JdbcTemplate jdbcTemplate;

    public ErrorGroupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<ErrorGroupResponse> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    private static ErrorGroupResponse mapRow(ResultSet rs) throws SQLException {
        Timestamp firstSeenTs = rs.getTimestamp("first_seen");
        Timestamp lastSeenTs = rs.getTimestamp("last_seen");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");

        return new ErrorGroupResponse(
                rs.getString("fingerprint"),
                rs.getString("error_type"),
                rs.getString("service_name"),
                rs.getString("message_template"),
                rs.getString("sample_stack_trace"),
                rs.getInt("occurrence_count"),
                firstSeenTs != null ? firstSeenTs.toInstant() : null,
                lastSeenTs != null ? lastSeenTs.toInstant() : null,
                createdTs != null ? createdTs.toInstant() : null,
                updatedTs != null ? updatedTs.toInstant() : null
        );
    }

    public ErrorGroupResponse upsert(String fingerprint, String errorType, String serviceName,
                                     String messageTemplate, String sampleStackTrace, Instant timestamp) {
        String sql = """
            INSERT INTO error_groups (
                fingerprint, error_type, service_name, message_template, sample_stack_trace,
                occurrence_count, first_seen, last_seen, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, 1, ?, ?, NOW(), NOW())
            ON CONFLICT (fingerprint) DO UPDATE SET
                occurrence_count = error_groups.occurrence_count + 1,
                last_seen = EXCLUDED.last_seen,
                sample_stack_trace = COALESCE(error_groups.sample_stack_trace, EXCLUDED.sample_stack_trace),
                updated_at = NOW()
            RETURNING *
            """;

        Timestamp ts = Timestamp.from(timestamp);
        return jdbcTemplate.queryForObject(sql, ROW_MAPPER,
                fingerprint, errorType, serviceName, messageTemplate, sampleStackTrace, ts, ts);
    }

    public Optional<ErrorGroupResponse> findByFingerprint(String fingerprint) {
        String sql = "SELECT * FROM error_groups WHERE fingerprint = ?";
        List<ErrorGroupResponse> results = jdbcTemplate.query(sql, ROW_MAPPER, fingerprint);
        return results.stream().findFirst();
    }

    public List<ErrorGroupResponse> findAll(String serviceName, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM error_groups WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (serviceName != null && !serviceName.isBlank()) {
            sql.append("AND service_name = ? ");
            params.add(serviceName.trim());
        }

        sql.append("ORDER BY last_seen DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long countAll(String serviceName) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM error_groups WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (serviceName != null && !serviceName.isBlank()) {
            sql.append("AND service_name = ? ");
            params.add(serviceName.trim());
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }
}
