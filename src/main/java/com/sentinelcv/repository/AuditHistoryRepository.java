package com.sentinelcv.repository;

import com.sentinelcv.entity.AuditHistory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AuditHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AuditHistory> rowMapper = (rs, rowNum) -> {
        AuditHistory history = new AuditHistory();
        history.setId(rs.getLong("id"));
        history.setAuditType(rs.getString("audit_type"));
        history.setTargetName(rs.getString("target_name"));
        history.setTargetId(rs.getLong("target_id"));
        history.setStatus(rs.getString("status"));
        history.setFindingsCount(rs.getInt("findings_count"));
        history.setAssessmentDecision(rs.getString("assessment_decision"));

        String startedAt = rs.getString("started_at");
        if (startedAt != null) {
            history.setStartedAt(LocalDateTime.parse(startedAt.replace(" ", "T")));
        }
        String completedAt = rs.getString("completed_at");
        if (completedAt != null) {
            history.setCompletedAt(LocalDateTime.parse(completedAt.replace(" ", "T")));
        }
        history.setSummary(rs.getString("summary"));

        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            history.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
        }
        return history;
    };

    public AuditHistory save(AuditHistory history) {
        String sql = "INSERT INTO audit_history (audit_type, target_name, target_id, status, findings_count, " +
                "assessment_decision, started_at, completed_at, summary, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, history.getAuditType());
            ps.setString(2, history.getTargetName());
            ps.setObject(3, history.getTargetId());
            ps.setString(4, history.getStatus());
            ps.setInt(5, history.getFindingsCount());
            ps.setString(6, history.getAssessmentDecision());
            ps.setString(7, history.getStartedAt() != null ? history.getStartedAt().toString().replace("T", " ") : null);
            ps.setString(8, history.getCompletedAt() != null ? history.getCompletedAt().toString().replace("T", " ") : null);
            ps.setString(9, history.getSummary());
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() != null) {
            history.setId(keyHolder.getKey().longValue());
        }
        return history;
    }

    public List<AuditHistory> findAll() {
        String sql = "SELECT * FROM audit_history ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<AuditHistory> findRecent(int limit) {
        String sql = "SELECT * FROM audit_history ORDER BY created_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper, limit);
    }
}
