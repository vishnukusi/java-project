package com.sentinelcv.repository;

import com.sentinelcv.entity.DatasetAudit;
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
public class DatasetAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public DatasetAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<DatasetAudit> rowMapper = (rs, rowNum) -> {
        DatasetAudit audit = new DatasetAudit();
        audit.setId(rs.getLong("id"));
        audit.setDatasetId(rs.getLong("dataset_id"));
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
        audit.setIntegrityScore(rs.getDouble("integrity_score"));

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

        // Try getting dataset name if joined
        try {
            audit.setDatasetName(rs.getString("dataset_name"));
        } catch (Exception ignored) {
        }

        return audit;
    };

    public DatasetAudit save(DatasetAudit audit) {
        if (audit.getId() == null) {
            String sql = "INSERT INTO dataset_audits (dataset_id, status, started_at, completed_at, total_findings, " +
                    "critical_findings, high_findings, medium_findings, low_findings, info_findings, integrity_score, " +
                    "assessment_decision, summary, error_message, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, audit.getDatasetId());
                ps.setString(2, audit.getStatus().name());
                ps.setString(3, audit.getStartedAt() != null ? audit.getStartedAt().toString().replace("T", " ") : null);
                ps.setString(4, audit.getCompletedAt() != null ? audit.getCompletedAt().toString().replace("T", " ") : null);
                ps.setInt(5, audit.getTotalFindings());
                ps.setInt(6, audit.getCriticalFindings());
                ps.setInt(7, audit.getHighFindings());
                ps.setInt(8, audit.getMediumFindings());
                ps.setInt(9, audit.getLowFindings());
                ps.setInt(10, audit.getInfoFindings());
                ps.setDouble(11, audit.getIntegrityScore());
                ps.setString(12, audit.getAssessmentDecision() != null ? audit.getAssessmentDecision().name() : null);
                ps.setString(13, audit.getSummary());
                ps.setString(14, audit.getErrorMessage());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                audit.setId(keyHolder.getKey().longValue());
            }
        } else {
            String sql = "UPDATE dataset_audits SET status = ?, started_at = ?, completed_at = ?, total_findings = ?, " +
                    "critical_findings = ?, high_findings = ?, medium_findings = ?, low_findings = ?, info_findings = ?, " +
                    "integrity_score = ?, assessment_decision = ?, summary = ?, error_message = ? WHERE id = ?";
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
                    audit.getIntegrityScore(),
                    audit.getAssessmentDecision() != null ? audit.getAssessmentDecision().name() : null,
                    audit.getSummary(),
                    audit.getErrorMessage(),
                    audit.getId());
        }
        return audit;
    }

    public Optional<DatasetAudit> findById(Long id) {
        String sql = "SELECT da.*, d.name as dataset_name FROM dataset_audits da " +
                "LEFT JOIN datasets d ON da.dataset_id = d.id WHERE da.id = ?";
        List<DatasetAudit> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public List<DatasetAudit> findByDatasetId(Long datasetId) {
        String sql = "SELECT da.*, d.name as dataset_name FROM dataset_audits da " +
                "LEFT JOIN datasets d ON da.dataset_id = d.id WHERE da.dataset_id = ? ORDER BY da.id DESC, da.created_at DESC";
        return jdbcTemplate.query(sql, rowMapper, datasetId);
    }

    public List<DatasetAudit> findAll() {
        String sql = "SELECT da.*, d.name as dataset_name FROM dataset_audits da " +
                "LEFT JOIN datasets d ON da.dataset_id = d.id ORDER BY da.id DESC, da.created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dataset_audits", Long.class);
        return c != null ? c : 0;
    }

    public int cleanupInterruptedAudits() {
        String sql = "UPDATE dataset_audits SET status = 'FAILED', " +
                "error_message = 'Audit interrupted by system shutdown/restart', " +
                "completed_at = datetime('now') WHERE status IN ('PENDING', 'RUNNING')";
        return jdbcTemplate.update(sql);
    }
}
