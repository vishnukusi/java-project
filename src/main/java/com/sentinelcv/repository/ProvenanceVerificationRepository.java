package com.sentinelcv.repository;

import com.sentinelcv.entity.ProvenanceVerification;
import com.sentinelcv.enums.VerificationStatus;
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
public class ProvenanceVerificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProvenanceVerificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<ProvenanceVerification> rowMapper = (rs, rowNum) -> {
        ProvenanceVerification v = new ProvenanceVerification();
        v.setId(rs.getLong("id"));
        v.setRecordId(rs.getString("record_id"));
        v.setInputHashStatus(VerificationStatus.valueOf(rs.getString("input_hash_status")));
        v.setModelHashStatus(VerificationStatus.valueOf(rs.getString("model_hash_status")));
        v.setConfigHashStatus(VerificationStatus.valueOf(rs.getString("config_hash_status")));
        v.setOutputHashStatus(VerificationStatus.valueOf(rs.getString("output_hash_status")));
        v.setChainLinkStatus(VerificationStatus.valueOf(rs.getString("chain_link_status")));
        v.setNonceStatus(VerificationStatus.valueOf(rs.getString("nonce_status")));
        v.setSequenceStatus(VerificationStatus.valueOf(rs.getString("sequence_status")));
        v.setSignatureStatus(VerificationStatus.valueOf(rs.getString("signature_status")));
        v.setTimestampStatus(VerificationStatus.valueOf(rs.getString("timestamp_status")));
        v.setOverallStatus(VerificationStatus.valueOf(rs.getString("overall_status")));
        v.setDetails(rs.getString("details"));

        String verifiedAt = rs.getString("verified_at");
        if (verifiedAt != null) {
            v.setVerifiedAt(LocalDateTime.parse(verifiedAt.replace(" ", "T")));
        }
        return v;
    };

    public ProvenanceVerification save(ProvenanceVerification v) {
        if (v.getId() == null) {
            String sql = "INSERT INTO provenance_verifications (record_id, input_hash_status, model_hash_status, " +
                    "config_hash_status, output_hash_status, chain_link_status, nonce_status, sequence_status, " +
                    "signature_status, timestamp_status, overall_status, details, verified_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, v.getRecordId());
                ps.setString(2, v.getInputHashStatus().name());
                ps.setString(3, v.getModelHashStatus().name());
                ps.setString(4, v.getConfigHashStatus().name());
                ps.setString(5, v.getOutputHashStatus().name());
                ps.setString(6, v.getChainLinkStatus().name());
                ps.setString(7, v.getNonceStatus().name());
                ps.setString(8, v.getSequenceStatus().name());
                ps.setString(9, v.getSignatureStatus().name());
                ps.setString(10, v.getTimestampStatus().name());
                ps.setString(11, v.getOverallStatus().name());
                ps.setString(12, v.getDetails());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                v.setId(keyHolder.getKey().longValue());
            }
        } else {
            String sql = "UPDATE provenance_verifications SET input_hash_status = ?, model_hash_status = ?, " +
                    "config_hash_status = ?, output_hash_status = ?, chain_link_status = ?, nonce_status = ?, " +
                    "sequence_status = ?, signature_status = ?, timestamp_status = ?, overall_status = ?, " +
                    "details = ?, verified_at = datetime('now') WHERE id = ?";
            jdbcTemplate.update(sql,
                    v.getInputHashStatus().name(),
                    v.getModelHashStatus().name(),
                    v.getConfigHashStatus().name(),
                    v.getOutputHashStatus().name(),
                    v.getChainLinkStatus().name(),
                    v.getNonceStatus().name(),
                    v.getSequenceStatus().name(),
                    v.getSignatureStatus().name(),
                    v.getTimestampStatus().name(),
                    v.getOverallStatus().name(),
                    v.getDetails(),
                    v.getId());
        }
        return v;
    }

    public Optional<ProvenanceVerification> findByRecordId(String recordId) {
        String sql = "SELECT * FROM provenance_verifications WHERE record_id = ? ORDER BY id DESC LIMIT 1";
        List<ProvenanceVerification> list = jdbcTemplate.query(sql, rowMapper, recordId);
        return list.stream().findFirst();
    }

    public List<ProvenanceVerification> findAll() {
        String sql = "SELECT * FROM provenance_verifications ORDER BY id DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }
}
