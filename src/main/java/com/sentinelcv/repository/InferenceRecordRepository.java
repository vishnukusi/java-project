package com.sentinelcv.repository;

import com.sentinelcv.entity.InferenceRecord;
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
public class InferenceRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public InferenceRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<InferenceRecord> rowMapper = (rs, rowNum) -> {
        InferenceRecord record = new InferenceRecord();
        record.setId(rs.getLong("id"));
        record.setRecordId(rs.getString("record_id"));
        record.setTimestamp(rs.getString("timestamp"));
        record.setModelIdentifier(rs.getString("model_identifier"));
        record.setModelHash(rs.getString("model_hash"));
        record.setInputImageHash(rs.getString("input_image_hash"));
        record.setConfigHash(rs.getString("config_hash"));
        record.setOutputHash(rs.getString("output_hash"));
        record.setNonce(rs.getString("nonce"));
        record.setSequenceNumber(rs.getInt("sequence_number"));
        record.setPreviousRecordHash(rs.getString("previous_record_hash"));
        record.setCurrentRecordHash(rs.getString("current_record_hash"));
        record.setSignature(rs.getString("signature"));
        record.setHmacValue(rs.getString("hmac_value"));
        record.setRawOutput(rs.getString("raw_output"));

        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            record.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
        }
        return record;
    };

    public InferenceRecord save(InferenceRecord record) {
        if (record.getId() == null) {
            String sql = "INSERT INTO inference_records (record_id, timestamp, model_identifier, model_hash, " +
                    "input_image_hash, config_hash, output_hash, nonce, sequence_number, previous_record_hash, " +
                    "current_record_hash, signature, hmac_value, raw_output, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, record.getRecordId());
                ps.setString(2, record.getTimestamp());
                ps.setString(3, record.getModelIdentifier());
                ps.setString(4, record.getModelHash());
                ps.setString(5, record.getInputImageHash());
                ps.setString(6, record.getConfigHash());
                ps.setString(7, record.getOutputHash());
                ps.setString(8, record.getNonce());
                ps.setObject(9, record.getSequenceNumber());
                ps.setString(10, record.getPreviousRecordHash());
                ps.setString(11, record.getCurrentRecordHash());
                ps.setString(12, record.getSignature());
                ps.setString(13, record.getHmacValue());
                ps.setString(14, record.getRawOutput());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                record.setId(keyHolder.getKey().longValue());
            }
        } else {
            String sql = "UPDATE inference_records SET timestamp = ?, model_identifier = ?, model_hash = ?, " +
                    "input_image_hash = ?, config_hash = ?, output_hash = ?, nonce = ?, sequence_number = ?, " +
                    "previous_record_hash = ?, current_record_hash = ?, signature = ?, hmac_value = ?, raw_output = ? " +
                    "WHERE id = ?";
            jdbcTemplate.update(sql,
                    record.getTimestamp(),
                    record.getModelIdentifier(),
                    record.getModelHash(),
                    record.getInputImageHash(),
                    record.getConfigHash(),
                    record.getOutputHash(),
                    record.getNonce(),
                    record.getSequenceNumber(),
                    record.getPreviousRecordHash(),
                    record.getCurrentRecordHash(),
                    record.getSignature(),
                    record.getHmacValue(),
                    record.getRawOutput(),
                    record.getId());
        }
        return record;
    }

    public Optional<InferenceRecord> findByRecordId(String recordId) {
        String sql = "SELECT * FROM inference_records WHERE record_id = ?";
        List<InferenceRecord> list = jdbcTemplate.query(sql, rowMapper, recordId);
        return list.stream().findFirst();
    }

    public List<InferenceRecord> findAllOrderedBySequence() {
        String sql = "SELECT * FROM inference_records ORDER BY sequence_number ASC, timestamp ASC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public List<InferenceRecord> findAll() {
        String sql = "SELECT * FROM inference_records ORDER BY id DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inference_records", Long.class);
        return c != null ? c : 0;
    }

    public boolean existsByNonce(String nonce) {
        Integer c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM inference_records WHERE nonce = ?", Integer.class, nonce);
        return c != null && c > 0;
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM provenance_verifications");
        jdbcTemplate.update("DELETE FROM inference_records");
    }
}
