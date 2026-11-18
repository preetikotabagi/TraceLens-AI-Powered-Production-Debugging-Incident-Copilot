package com.tracelens.repository;

import com.tracelens.dto.IncidentResponse;
import com.tracelens.dto.IncidentStatus;
import com.tracelens.dto.Severity;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class IncidentRepository {

    private final JdbcTemplate jdbcTemplate;

    public IncidentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<IncidentResponse> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

    private static IncidentResponse mapRow(ResultSet rs) throws SQLException {
        Timestamp firstSeenTs = rs.getTimestamp("first_seen");
        Timestamp lastSeenTs = rs.getTimestamp("last_seen");
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");

        return new IncidentResponse(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("service_name"),
                Severity.valueOf(rs.getString("severity")),
                IncidentStatus.valueOf(rs.getString("status")),
                rs.getString("fingerprint"),
                rs.getString("trigger_rule"),
                rs.getInt("occurrence_count"),
                firstSeenTs != null ? firstSeenTs.toInstant() : null,
                lastSeenTs != null ? lastSeenTs.toInstant() : null,
                rs.getString("summary"),
                createdTs != null ? createdTs.toInstant() : null,
                updatedTs != null ? updatedTs.toInstant() : null);
    }

    public IncidentResponse create(String title, String serviceName, Severity severity, IncidentStatus status,
            String fingerprint, String triggerRule, int occurrenceCount,
            Instant firstSeen, Instant lastSeen, String summary) {
        String sql = """
                INSERT INTO incidents (
                    title, service_name, severity, status, fingerprint, trigger_rule,
                    occurrence_count, first_seen, last_seen, summary, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, title);
            ps.setString(2, serviceName);
            ps.setString(3, severity.name());
            ps.setString(4, status.name());
            ps.setString(5, fingerprint);
            ps.setString(6, triggerRule);
            ps.setInt(7, occurrenceCount);
            ps.setTimestamp(8, Timestamp.from(firstSeen));
            ps.setTimestamp(9, Timestamp.from(lastSeen));
            ps.setString(10, summary);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key != null ? key.longValue() : 0L;
        return findById(id).orElseThrow();
    }

    public Optional<IncidentResponse> findById(Long id) {
        String sql = "SELECT * FROM incidents WHERE id = ?";
        List<IncidentResponse> results = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    public Optional<IncidentResponse> findActiveByFingerprint(String fingerprint) {
        String sql = """
                SELECT * FROM incidents
                WHERE fingerprint = ? AND status IN ('OPEN', 'INVESTIGATING')
                ORDER BY last_seen DESC LIMIT 1
                """;
        List<IncidentResponse> results = jdbcTemplate.query(sql, ROW_MAPPER, fingerprint);
        return results.stream().findFirst();
    }

    public Optional<IncidentResponse> findActiveByService(String serviceName) {
        String sql = """
                SELECT * FROM incidents
                WHERE service_name = ? AND status IN ('OPEN', 'INVESTIGATING')
                ORDER BY last_seen DESC LIMIT 1
                """;
        List<IncidentResponse> results = jdbcTemplate.query(sql, ROW_MAPPER, serviceName);
        return results.stream().findFirst();
    }

    public void incrementOccurrence(Long id, int additionalOccurrences, Instant lastSeen, Severity severity) {
        String sql = """
                UPDATE incidents SET
                    occurrence_count = occurrence_count + ?,
                    last_seen = ?,
                    severity = CASE
                        WHEN ? = 'CRITICAL' THEN 'CRITICAL'
                        WHEN ? = 'HIGH' AND severity != 'CRITICAL' THEN 'HIGH'
                        ELSE severity
                    END,
                    updated_at = NOW()
                WHERE id = ?
                """;
        jdbcTemplate.update(sql, additionalOccurrences, Timestamp.from(lastSeen),
                severity.name(), severity.name(), id);
    }

    public boolean updateStatus(Long id, IncidentStatus status) {
        String sql = "UPDATE incidents SET status = ?, updated_at = NOW() WHERE id = ?";
        int updated = jdbcTemplate.update(sql, status.name(), id);
        return updated > 0;
    }

    public List<IncidentResponse> findAll(String serviceName, Severity severity, IncidentStatus status,
            int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM incidents WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (serviceName != null && !serviceName.isBlank()) {
            sql.append("AND service_name = ? ");
            params.add(serviceName.trim());
        }
        if (severity != null) {
            sql.append("AND severity = ? ");
            params.add(severity.name());
        }
        if (status != null) {
            sql.append("AND status = ? ");
            params.add(status.name());
        }

        sql.append("ORDER BY last_seen DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public long countAll(String serviceName, Severity severity, IncidentStatus status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM incidents WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (serviceName != null && !serviceName.isBlank()) {
            sql.append("AND service_name = ? ");
            params.add(serviceName.trim());
        }
        if (severity != null) {
            sql.append("AND severity = ? ");
            params.add(severity.name());
        }
        if (status != null) {
            sql.append("AND status = ? ");
            params.add(status.name());
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public long countActiveIncidents() {
        String sql = "SELECT COUNT(*) FROM incidents WHERE status IN ('OPEN', 'INVESTIGATING')";
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0L;
    }

    public Map<String, Long> getSeverityDistribution() {
        String sql = "SELECT severity, COUNT(*) as cnt FROM incidents GROUP BY severity";
        Map<String, Long> map = new HashMap<>();
        for (Severity s : Severity.values()) {
            map.put(s.name(), 0L);
        }
        jdbcTemplate.query(sql, rs -> {
            map.put(rs.getString("severity"), rs.getLong("cnt"));
        });
        return map;
    }

    public List<IncidentResponse> findRecent(int limit) {
        String sql = "SELECT * FROM incidents ORDER BY last_seen DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, limit);
    }
}
