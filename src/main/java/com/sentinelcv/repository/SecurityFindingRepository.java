package com.sentinelcv.repository;

import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.FindingSeverity;
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
public class SecurityFindingRepository {

    private final JdbcTemplate jdbcTemplate;

    public SecurityFindingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<SecurityFinding> rowMapper = (rs, rowNum) -> {
        SecurityFinding finding = new SecurityFinding();
        finding.setId(rs.getLong("id"));
        finding.setFindingId(rs.getString("finding_id"));
        finding.setModule(rs.getString("module"));
        finding.setCategory(rs.getString("category"));
        finding.setDescription(rs.getString("description"));
        finding.setSeverity(FindingSeverity.valueOf(rs.getString("severity")));
        finding.setConfidence(rs.getDouble("confidence"));
        finding.setEvidence(rs.getString("evidence"));
        finding.setAffectedAsset(rs.getString("affected_asset"));
        finding.setRecommendedAction(rs.getString("recommended_action"));
        finding.setSourceAuditId(rs.getLong("source_audit_id"));
        finding.setSourceAuditType(rs.getString("source_audit_type"));

        String detectedAt = rs.getString("detected_at");
        if (detectedAt != null) {
            finding.setDetectedAt(LocalDateTime.parse(detectedAt.replace(" ", "T")));
        }
        return finding;
    };

    public SecurityFinding save(SecurityFinding finding) {
        if (finding.getId() == null) {
            String sql = "INSERT OR REPLACE INTO security_findings (finding_id, module, category, description, " +
                    "severity, confidence, evidence, affected_asset, recommended_action, source_audit_id, " +
                    "source_audit_type, detected_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, finding.getFindingId());
                ps.setString(2, finding.getModule());
                ps.setString(3, finding.getCategory());
                ps.setString(4, finding.getDescription());
                ps.setString(5, finding.getSeverity().name());
                ps.setDouble(6, finding.getConfidence());
                ps.setString(7, finding.getEvidence());
                ps.setString(8, finding.getAffectedAsset());
                ps.setString(9, finding.getRecommendedAction());
                ps.setObject(10, finding.getSourceAuditId());
                ps.setString(11, finding.getSourceAuditType());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                finding.setId(keyHolder.getKey().longValue());
            }
        } else {
            String sql = "UPDATE security_findings SET module = ?, category = ?, description = ?, severity = ?, " +
                    "confidence = ?, evidence = ?, affected_asset = ?, recommended_action = ? WHERE id = ?";
            jdbcTemplate.update(sql,
                    finding.getModule(),
                    finding.getCategory(),
                    finding.getDescription(),
                    finding.getSeverity().name(),
                    finding.getConfidence(),
                    finding.getEvidence(),
                    finding.getAffectedAsset(),
                    finding.getRecommendedAction(),
                    finding.getId());
        }
        return finding;
    }

    public List<SecurityFinding> findAll() {
        String sql = "SELECT * FROM security_findings ORDER BY id DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<SecurityFinding> findByModule(String module) {
        String sql = "SELECT * FROM security_findings WHERE module = ? ORDER BY id DESC";
        return jdbcTemplate.query(sql, rowMapper, module);
    }

    public List<SecurityFinding> findBySeverity(FindingSeverity severity) {
        String sql = "SELECT * FROM security_findings WHERE severity = ? ORDER BY id DESC";
        return jdbcTemplate.query(sql, rowMapper, severity.name());
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM security_findings", Long.class);
        return c != null ? c : 0;
    }

    public long countBySeverity(FindingSeverity severity) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM security_findings WHERE severity = ?", Long.class, severity.name());
        return c != null ? c : 0;
    }

    public void deleteBySource(String sourceAuditType, Long sourceAuditId) {
        jdbcTemplate.update("DELETE FROM security_findings WHERE source_audit_type = ? AND source_audit_id = ?", sourceAuditType, sourceAuditId);
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM security_findings");
    }
}
