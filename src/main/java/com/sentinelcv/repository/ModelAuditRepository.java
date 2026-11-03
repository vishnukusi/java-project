package com.sentinelcv.repository;

import com.sentinelcv.entity.ModelAudit;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.AuditStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ModelAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ModelAudit> rowMapper = (rs, rowNum) -> {
        ModelAudit audit = new ModelAudit();
        audit.setId(rs.getLong("id"));
        audit.setModelId(rs.getLong("model_id"));
        audit.setStatus(AuditStatus.valueOf(rs.getString("status")));

        String startedAt = rs.getString("started_at");
        if (startedAt != null) {
            audit.setStartedAt(LocalDateTime.parse(startedAt.replace(" ", "T")));
        }
        String completedAt = rs.getString("completed_at");
        if (completedAt != null) {
            audit.setCompletedAt(LocalDateTime.parse(completedAt.replace(" ", "T")));
        }

        audit.setTotalFindings(rs.getInt("total_findings"));
        audit.setCriticalFindings(rs.getInt("critical_findings"));
        audit.setHighFindings(rs.getInt("high_findings"));
        audit.setMediumFindings(rs.getInt("medium_findings"));
        audit.setLowFindings(rs.getInt("low_findings"));
        audit.setInfoFindings(rs.getInt("info_findings"));
        audit.setBehavioralFingerprint(rs.getString("behavioral_fingerprint"));

        String decision = rs.getString("assessment_decision");
        if (decision != null) {
            audit.setAssessmentDecision(AssessmentDecision.valueOf(decision));
        }

        audit.setSummary(rs.getString("summary"));
        audit.setErrorMessage(rs.getString("error_message"));

        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            audit.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
        }

        try {
            audit.setModelName(rs.getString("model_name"));
        } catch (Exception ignored) {
        }

        return audit;
    };

    public ModelAudit save(ModelAudit audit) {
        if (audit.getId() == null) {
            String sql = "INSERT INTO model_audits (model_id, status, started_at, completed_at, total_findings, " +
                    "critical_findings, high_findings, medium_findings, low_findings, info_findings, behavioral_fingerprint, " +
                    "assessment_decision, summary, error_message, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, audit.getModelId());
                ps.setString(2, audit.getStatus().name());
                ps.setString(3, audit.getStartedAt() != null ? audit.getStartedAt().toString().replace("T", " ") : null);
                ps.setString(4, audit.getCompletedAt() != null ? audit.getCompletedAt().toString().replace("T", " ") : null);
                ps.setInt(5, audit.getTotalFindings());
                ps.setInt(6, audit.getCriticalFindings());
                ps.setInt(7, audit.getHighFindings());
                ps.setInt(8, audit.getMediumFindings());
                ps.setInt(9, audit.getLowFindings());
                ps.setInt(10, audit.getInfoFindings());
                ps.setString(11, audit.getBehavioralFingerprint());
                ps.setString(12, audit.getAssessmentDecision() != null ? audit.getAssessmentDecision().name() : null);
                ps.setString(13, audit.getSummary());
                ps.setString(14, audit.getErrorMessage());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                audit.setId(keyHolder.getKey().longValue());
            }
        } else {
            String sql = "UPDATE model_audits SET status = ?, started_at = ?, completed_at = ?, total_findings = ?, " +
                    "critical_findings = ?, high_findings = ?, medium_findings = ?, low_findings = ?, info_findings = ?, " +
                    "behavioral_fingerprint = ?, assessment_decision = ?, summary = ?, error_message = ? WHERE id = ?";
            jdbcTemplate.update(sql,
                    audit.getStatus().name(),
                    audit.getStartedAt() != null ? audit.getStartedAt().toString().replace("T", " ") : null,
                    audit.getCompletedAt() != null ? audit.getCompletedAt().toString().replace("T", " ") : null,
                    audit.getTotalFindings(),
                    audit.getCriticalFindings(),
                    audit.getHighFindings(),
                    audit.getMediumFindings(),
                    audit.getLowFindings(),
                    audit.getInfoFindings(),
                    audit.getBehavioralFingerprint(),
                    audit.getAssessmentDecision() != null ? audit.getAssessmentDecision().name() : null,
                    audit.getSummary(),
                    audit.getErrorMessage(),
                    audit.getId());
        }
        return audit;
    }

    public Optional<ModelAudit> findById(Long id) {
        String sql = "SELECT ma.*, m.name as model_name FROM model_audits ma " +
                "LEFT JOIN models m ON ma.model_id = m.id WHERE ma.id = ?";
        List<ModelAudit> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public List<ModelAudit> findByModelId(Long modelId) {
        String sql = "SELECT ma.*, m.name as model_name FROM model_audits ma " +
                "LEFT JOIN models m ON ma.model_id = m.id WHERE ma.model_id = ? ORDER BY ma.id DESC, ma.created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, modelId);
    }

    public List<ModelAudit> findAll() {
        String sql = "SELECT ma.*, m.name as model_name FROM model_audits ma " +
                "LEFT JOIN models m ON ma.model_id = m.id ORDER BY ma.id DESC, ma.created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM model_audits", Long.class);
        return c != null ? c : 0;
    }

    public int cleanupInterruptedAudits() {
        String sql = "UPDATE model_audits SET status = 'FAILED', " +
                "error_message = 'Audit interrupted by system shutdown/restart', " +
                "completed_at = datetime('now') WHERE status IN ('PENDING', 'RUNNING')";
        return jdbcTemplate.update(sql);
    }
}
