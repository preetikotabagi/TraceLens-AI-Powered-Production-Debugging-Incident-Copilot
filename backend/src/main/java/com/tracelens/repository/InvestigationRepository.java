package com.tracelens.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracelens.dto.AiInvestigationResponse;
import com.tracelens.dto.RecommendationDto;
import com.tracelens.dto.RootCauseHypothesisDto;
import com.tracelens.dto.SuspiciousEventDto;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class InvestigationRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public InvestigationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private RowMapper<AiInvestigationResponse> rowMapper() {
        return (rs, rowNum) -> mapRow(rs);
    }

    private AiInvestigationResponse mapRow(ResultSet rs) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");

        List<SuspiciousEventDto> suspiciousSequence = new ArrayList<>();
        List<RootCauseHypothesisDto> hypotheses = new ArrayList<>();
        List<RecommendationDto> recommendations = new ArrayList<>();

        try {
            String seqJson = rs.getString("suspicious_sequence");
            if (seqJson != null && !seqJson.isBlank()) {
                suspiciousSequence = objectMapper.readValue(seqJson, new TypeReference<>() {});
            }
            String hypJson = rs.getString("hypotheses");
            if (hypJson != null && !hypJson.isBlank()) {
                hypotheses = objectMapper.readValue(hypJson, new TypeReference<>() {});
            }
            String recJson = rs.getString("recommendations");
            if (recJson != null && !recJson.isBlank()) {
                recommendations = objectMapper.readValue(recJson, new TypeReference<>() {});
            }
        } catch (Exception e) {
            // fallback gracefully
        }

        return new AiInvestigationResponse(
                rs.getLong("id"),
                rs.getLong("incident_id"),
                rs.getString("model"),
                rs.getString("summary"),
                suspiciousSequence,
                hypotheses,
                recommendations,
                rs.getString("raw_response"),
                createdTs != null ? createdTs.toInstant() : null
        );
    }

    public AiInvestigationResponse save(Long incidentId, String model, String summary,
                                        List<SuspiciousEventDto> suspiciousSequence,
                                        List<RootCauseHypothesisDto> hypotheses,
                                        List<RecommendationDto> recommendations,
                                        String rawResponse) {
        String sql = """
            INSERT INTO incident_investigations (
                incident_id, model, summary, suspicious_sequence, hypotheses,
                recommendations, raw_response, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            """;

        String seqJson;
        String hypJson;
        String recJson;
        try {
            seqJson = objectMapper.writeValueAsString(suspiciousSequence);
            hypJson = objectMapper.writeValueAsString(hypotheses);
            recJson = objectMapper.writeValueAsString(recommendations);
        } catch (Exception e) {
            seqJson = "[]";
            hypJson = "[]";
            recJson = "[]";
        }

        final String finalSeqJson = seqJson;
        final String finalHypJson = hypJson;
        final String finalRecJson = recJson;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setLong(1, incidentId);
            ps.setString(2, model);
            ps.setString(3, summary);
            ps.setString(4, finalSeqJson);
            ps.setString(5, finalHypJson);
            ps.setString(6, finalRecJson);
            ps.setString(7, rawResponse);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        long id = key != null ? key.longValue() : 0L;
        return findById(id).orElseThrow();
    }

    public Optional<AiInvestigationResponse> findById(Long id) {
        String sql = "SELECT * FROM incident_investigations WHERE id = ?";
        List<AiInvestigationResponse> results = jdbcTemplate.query(sql, rowMapper(), id);
        return results.stream().findFirst();
    }

    public List<AiInvestigationResponse> findByIncidentId(Long incidentId) {
        String sql = "SELECT * FROM incident_investigations WHERE incident_id = ? ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper(), incidentId);
    }

    public Optional<AiInvestigationResponse> findLatestByIncidentId(Long incidentId) {
        String sql = "SELECT * FROM incident_investigations WHERE incident_id = ? ORDER BY created_at DESC LIMIT 1";
        List<AiInvestigationResponse> results = jdbcTemplate.query(sql, rowMapper(), incidentId);
        return results.stream().findFirst();
    }
}
