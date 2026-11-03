package com.sentinelcv.repository;

import com.sentinelcv.entity.MlModel;
import com.sentinelcv.enums.ModelFormat;
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
public class MlModelRepository {

    private final JdbcTemplate jdbcTemplate;

    public MlModelRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MlModel> rowMapper = (rs, rowNum) -> {
        MlModel model = new MlModel();
        model.setId(rs.getLong("id"));
        model.setName(rs.getString("name"));
        model.setFormat(ModelFormat.valueOf(rs.getString("format")));
        model.setPath(rs.getString("path"));
        model.setFileSizeBytes(rs.getLong("file_size_bytes"));
        model.setSha256Hash(rs.getString("sha256_hash"));
        model.setMetadataJson(rs.getString("metadata_json"));
        model.setInputInfo(rs.getString("input_info"));
        model.setOutputInfo(rs.getString("output_info"));

        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            model.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
        }
        String updatedAt = rs.getString("updated_at");
        if (updatedAt != null) {
            model.setUpdatedAt(LocalDateTime.parse(updatedAt.replace(" ", "T")));
        }
        return model;
    };

    public MlModel save(MlModel model) {
        if (model.getId() == null) {
            String sql = "INSERT INTO models (name, format, path, file_size_bytes, sha256_hash, metadata_json, input_info, output_info, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'))";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, model.getName());
                ps.setString(2, model.getFormat().name());
                ps.setString(3, model.getPath());
                ps.setLong(4, model.getFileSizeBytes());
                ps.setString(5, model.getSha256Hash());
                ps.setString(6, model.getMetadataJson());
                ps.setString(7, model.getInputInfo());
                ps.setString(8, model.getOutputInfo());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                model.setId(keyHolder.getKey().longValue());
            }
        } else {
            String sql = "UPDATE models SET name = ?, format = ?, path = ?, file_size_bytes = ?, sha256_hash = ?, " +
                    "metadata_json = ?, input_info = ?, output_info = ?, updated_at = datetime('now') WHERE id = ?";
            jdbcTemplate.update(sql,
                    model.getName(),
                    model.getFormat().name(),
                    model.getPath(),
                    model.getFileSizeBytes(),
                    model.getSha256Hash(),
                    model.getMetadataJson(),
                    model.getInputInfo(),
                    model.getOutputInfo(),
                    model.getId());
        }
        return model;
    }

    public Optional<MlModel> findById(Long id) {
        String sql = "SELECT * FROM models WHERE id = ?";
        List<MlModel> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public List<MlModel> findAll() {
        String sql = "SELECT * FROM models ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM models", Long.class);
        return c != null ? c : 0;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM model_findings WHERE model_id = ?", id);
        jdbcTemplate.update("DELETE FROM model_audits WHERE model_id = ?", id);
        jdbcTemplate.update("DELETE FROM models WHERE id = ?", id);
    }
}
