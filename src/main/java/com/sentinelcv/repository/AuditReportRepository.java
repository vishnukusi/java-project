package com.sentinelcv.repository;

import com.sentinelcv.entity.AuditReport;
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
public class AuditReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<AuditReport> rowMapper = (rs, rowNum) -> {
        AuditReport report = new AuditReport();
        report.setId(rs.getLong("id"));
        report.setTitle(rs.getString("title"));
        report.setReportType(rs.getString("report_type"));
        report.setFormat(rs.getString("format"));
        report.setFilePath(rs.getString("file_path"));
        report.setAssessmentDecision(rs.getString("assessment_decision"));
        report.setTotalFindings(rs.getInt("total_findings"));
        report.setCriticalCount(rs.getInt("critical_count"));
        report.setHighCount(rs.getInt("high_count"));
        report.setMediumCount(rs.getInt("medium_count"));
        report.setLowCount(rs.getInt("low_count"));
        report.setInfoCount(rs.getInt("info_count"));
        report.setSummary(rs.getString("summary"));

        String generatedAt = rs.getString("generated_at");
        if (generatedAt != null) {
            report.setGeneratedAt(LocalDateTime.parse(generatedAt.replace(" ", "T")));
        }
        return report;
    };

    public AuditReport save(AuditReport report) {
        String sql = "INSERT INTO audit_reports (title, report_type, format, file_path, assessment_decision, " +
                "total_findings, critical_count, high_count, medium_count, low_count, info_count, summary, generated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, report.getTitle());
            ps.setString(2, report.getReportType());
            ps.setString(3, report.getFormat());
            ps.setString(4, report.getFilePath());
            ps.setString(5, report.getAssessmentDecision());
            ps.setInt(6, report.getTotalFindings());
            ps.setInt(7, report.getCriticalCount());
            ps.setInt(8, report.getHighCount());
            ps.setInt(9, report.getMediumCount());
            ps.setInt(10, report.getLowCount());
            ps.setInt(11, report.getInfoCount());
            ps.setString(12, report.getSummary());
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() != null) {
            report.setId(keyHolder.getKey().longValue());
        }
        return report;
    }

    public Optional<AuditReport> findById(Long id) {
        String sql = "SELECT * FROM audit_reports WHERE id = ?";
        List<AuditReport> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public List<AuditReport> findAll() {
        String sql = "SELECT * FROM audit_reports ORDER BY generated_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }
}
