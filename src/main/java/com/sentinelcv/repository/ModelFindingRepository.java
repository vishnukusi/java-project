package com.sentinelcv.repository;

import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.FindingSeverity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ModelFindingRepository {

    private final JdbcTemplate jdbcTemplate;

    public ModelFindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SecurityFinding> rowMapper = (rs, rowNum) -> {
        SecurityFinding finding = new SecurityFinding();
        finding.setId(rs.getLong("id"));
        finding.setFindingId(rs.getString("finding_id"));
        finding.setModule("MODEL");
        finding.setCategory(rs.getString("category"));
        finding.setDescription(rs.getString("description"));
        finding.setSeverity(FindingSeverity.valueOf(rs.getString("severity")));
        finding.setConfidence(rs.getDouble("confidence"));
        finding.setEvidence(rs.getString("evidence"));
        finding.setAffectedAsset(rs.getString("affected_asset"));
        finding.setRecommendedAction(rs.getString("recommended_action"));
        finding.setSourceAuditId(rs.getLong("audit_id"));
        finding.setSourceAuditType("MODEL");

        String detectedAt = rs.getString("detected_at");
        if (detectedAt != null) {
            finding.setDetectedAt(LocalDateTime.parse(detectedAt.replace(" ", "T")));
        }
        return finding;
    };

    public void save(SecurityFinding finding, Long modelId) {
        String sql = "INSERT INTO model_findings (audit_id, model_id, finding_id, category, description, severity, " +
                "confidence, evidence, affected_asset, recommended_action, detected_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";
        jdbcTemplate.update(sql,
                finding.getSourceAuditId(),
                modelId,
                finding.getFindingId(),
                finding.getCategory(),
                finding.getDescription(),
                finding.getSeverity().name(),
                finding.getConfidence(),
                finding.getEvidence(),
                finding.getAffectedAsset(),
                finding.getRecommendedAction());
    }

    public List<SecurityFinding> findByAuditId(Long auditId) {
        String sql = "SELECT * FROM model_findings WHERE audit_id = ? ORDER BY id ASC";
        return jdbcTemplate.query(sql, rowMapper, auditId);
    }

    public List<SecurityFinding> findByModelId(Long modelId) {
        String sql = "SELECT * FROM model_findings WHERE model_id = ? ORDER BY id DESC";
        return jdbcTemplate.query(sql, rowMapper, modelId);
    }
}
