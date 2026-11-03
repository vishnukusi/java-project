package com.sentinelcv.repository;

import com.sentinelcv.entity.MandatoryCheckRecord;
import com.sentinelcv.enums.CheckExecutionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MandatoryCheckStatusRepository {

    private final JdbcTemplate jdbcTemplate;

    public MandatoryCheckStatusRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MandatoryCheckRecord> rowMapper = (rs, rowNum) -> {
        MandatoryCheckRecord record = new MandatoryCheckRecord();
        record.setCheckId(rs.getString("check_id"));
        long auditId = rs.getLong("audit_id");
        if (!rs.wasNull()) {
            record.setAuditId(auditId);
        }
        record.setAssetId(rs.getString("asset_id"));
        record.setAssetHash(rs.getString("asset_hash"));
        String ts = rs.getString("execution_timestamp");
        if (ts != null) {
            record.setExecutionTimestamp(LocalDateTime.parse(ts.replace(" ", "T")));
        }
        String statusStr = rs.getString("execution_status");
        if (statusStr != null) {
            try {
                record.setExecutionStatus(CheckExecutionStatus.valueOf(statusStr));
            } catch (Exception e) {
                record.setExecutionStatus(CheckExecutionStatus.NOT_EXECUTED);
            }
        }
        record.setDetails(rs.getString("details"));
        return record;
    };

    public MandatoryCheckRecord save(MandatoryCheckRecord record) {
        String sql = "INSERT INTO mandatory_check_statuses (check_id, audit_id, asset_id, asset_hash, execution_timestamp, execution_status, details) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(check_id) DO UPDATE SET audit_id = excluded.audit_id, asset_id = excluded.asset_id, " +
                "asset_hash = excluded.asset_hash, execution_timestamp = excluded.execution_timestamp, " +
                "execution_status = excluded.execution_status, details = excluded.details";
        jdbcTemplate.update(sql,
                record.getCheckId(),
                record.getAuditId(),
                record.getAssetId(),
                record.getAssetHash(),
                record.getExecutionTimestamp() != null ? record.getExecutionTimestamp().toString().replace("T", " ") : LocalDateTime.now().toString().replace("T", " "),
                record.getExecutionStatus() != null ? record.getExecutionStatus().name() : CheckExecutionStatus.NOT_EXECUTED.name(),
                record.getDetails());
        return record;
    }

    public Optional<MandatoryCheckRecord> findById(String checkId) {
        String sql = "SELECT * FROM mandatory_check_statuses WHERE check_id = ?";
        List<MandatoryCheckRecord> list = jdbcTemplate.query(sql, rowMapper, checkId);
        return list.stream().findFirst();
    }

    public List<MandatoryCheckRecord> findAll() {
        String sql = "SELECT * FROM mandatory_check_statuses";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public void invalidate(String checkId) {
        String sql = "UPDATE mandatory_check_statuses SET execution_status = 'NOT_EXECUTED', details = 'Invalidated' WHERE check_id = ?";
        jdbcTemplate.update(sql, checkId);
    }

    public void invalidateByAsset(String assetId) {
        String sql = "UPDATE mandatory_check_statuses SET execution_status = 'NOT_EXECUTED', details = 'Invalidated due to asset modification' WHERE asset_id = ?";
        jdbcTemplate.update(sql, assetId);
    }

    public void invalidateAll() {
        String sql = "UPDATE mandatory_check_statuses SET execution_status = 'NOT_EXECUTED', details = 'System-wide invalidation'";
        jdbcTemplate.update(sql);
    }

    public void deleteAll() {
        String sql = "DELETE FROM mandatory_check_statuses";
        jdbcTemplate.update(sql);
    }
}

