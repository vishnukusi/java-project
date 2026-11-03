package com.sentinelcv.repository;

import com.sentinelcv.entity.DriftAudit;
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
public class DriftAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public DriftAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<DriftAudit> rowMapper = (rs, rowNum) -> {
        DriftAudit audit = new DriftAudit();
        audit.setId(rs.getLong("id"));
        audit.setReferenceDatasetId(rs.getLong("reference_dataset_id"));
        audit.setEvaluationDatasetId(rs.getLong("evaluation_dataset_id"));
        audit.setStatus(rs.getString("status"));
        audit.setDriftDetected(rs.getInt("drift_detected") == 1);
        audit.setOverallDriftScore(rs.getDouble("overall_drift_score"));
        audit.setSeverity(rs.getString("severity"));
        audit.setMetricsJson(rs.getString("metrics_json"));
        audit.setSummary(rs.getString("summary"));

        String startedAt = rs.getString("started_at");
        if (startedAt != null) {
            audit.setStartedAt(LocalDateTime.parse(startedAt.replace(" ", "T")));
        }
        String completedAt = rs.getString("completed_at");
        if (completedAt != null) {
            audit.setCompletedAt(LocalDateTime.parse(completedAt.replace(" ", "T")));
        }
        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            audit.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
        }

        try {
            audit.setReferenceDatasetName(rs.getString("ref_name"));
            audit.setEvaluationDatasetName(rs.getString("eval_name"));
        } catch (Exception ignored) {
        }

        return audit;
    };

    public DriftAudit save(DriftAudit audit) {
        if (audit.getId() == null) {
            String sql = "INSERT INTO drift_audits (reference_dataset_id, evaluation_dataset_id, status, " +
                    "drift_detected, overall_drift_score, severity, metrics_json, summary, started_at, completed_at, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, audit.getReferenceDatasetId());
                ps.setLong(2, audit.getEvaluationDatasetId());
                ps.setString(3, audit.getStatus());
                ps.setInt(4, audit.isDriftDetected() ? 1 : 0);
                ps.setDouble(5, audit.getOverallDriftScore());
                ps.setString(6, audit.getSeverity());
                ps.setString(7, audit.getMetricsJson());
                ps.setString(8, audit.getSummary());
                ps.setString(9, audit.getStartedAt() != null ? audit.getStartedAt().toString().replace("T", " ") : null);
                ps.setString(10, audit.getCompletedAt() != null ? audit.getCompletedAt().toString().replace("T", " ") : null);
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                audit.setId(keyHolder.getKey().longValue());
            }
        } else {
            String sql = "UPDATE drift_audits SET status = ?, drift_detected = ?, overall_drift_score = ?, " +
                    "severity = ?, metrics_json = ?, summary = ?, started_at = ?, completed_at = ? WHERE id = ?";
            jdbcTemplate.update(sql,
                    audit.getStatus(),
                    audit.isDriftDetected() ? 1 : 0,
                    audit.getOverallDriftScore(),
                    audit.getSeverity(),
                    audit.getMetricsJson(),
                    audit.getSummary(),
                    audit.getStartedAt() != null ? audit.getStartedAt().toString().replace("T", " ") : null,
                    audit.getCompletedAt() != null ? audit.getCompletedAt().toString().replace("T", " ") : null,
                    audit.getId());
        }
        return audit;
    }

    public Optional<DriftAudit> findById(Long id) {
        String sql = "SELECT da.*, d1.name as ref_name, d2.name as eval_name " +
                "FROM drift_audits da " +
                "LEFT JOIN datasets d1 ON da.reference_dataset_id = d1.id " +
                "LEFT JOIN datasets d2 ON da.evaluation_dataset_id = d2.id " +
                "WHERE da.id = ?";
        List<DriftAudit> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public List<DriftAudit> findAll() {
        String sql = "SELECT da.*, d1.name as ref_name, d2.name as eval_name " +
                "FROM drift_audits da " +
                "LEFT JOIN datasets d1 ON da.reference_dataset_id = d1.id " +
                "LEFT JOIN datasets d2 ON da.evaluation_dataset_id = d2.id " +
                "ORDER BY da.created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM drift_audits", Long.class);
        return c != null ? c : 0;
    }

    public int cleanupInterruptedAudits() {
        String sql = "UPDATE drift_audits SET status = 'FAILED', " +
                "summary = 'Audit interrupted by system shutdown/restart', " +
                "completed_at = datetime('now') WHERE status IN ('PENDING', 'RUNNING')";
        return jdbcTemplate.update(sql);
    }
}
