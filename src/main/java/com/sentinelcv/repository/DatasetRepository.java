package com.sentinelcv.repository;

import com.sentinelcv.entity.Dataset;
import com.sentinelcv.enums.DatasetFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class DatasetRepository {

    private final JdbcTemplate jdbcTemplate;

    public DatasetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Dataset> rowMapper = (rs, rowNum) -> {
        Dataset dataset = new Dataset();
        dataset.setId(rs.getLong("id"));
        dataset.setName(rs.getString("name"));
        dataset.setFormat(DatasetFormat.valueOf(rs.getString("format")));
        dataset.setPath(rs.getString("path"));
        dataset.setTotalImages(rs.getInt("total_images"));
        dataset.setTotalAnnotations(rs.getInt("total_annotations"));
        dataset.setTotalClasses(rs.getInt("total_classes"));
        dataset.setFileSizeBytes(rs.getLong("file_size_bytes"));
        dataset.setSha256Hash(rs.getString("sha256_hash"));
        String createdAt = rs.getString("created_at");
        if (createdAt != null) {
            dataset.setCreatedAt(LocalDateTime.parse(createdAt.replace(" ", "T")));
        }
        String updatedAt = rs.getString("updated_at");
        if (updatedAt != null) {
            dataset.setUpdatedAt(LocalDateTime.parse(updatedAt.replace(" ", "T")));
        }
        return dataset;
    };

    public Dataset save(Dataset dataset) {
        if (dataset.getId() == null) {
            String sql = "INSERT INTO datasets (name, format, path, total_images, total_annotations, total_classes, file_size_bytes, sha256_hash, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'))";
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, dataset.getName());
                ps.setString(2, dataset.getFormat().name());
                ps.setString(3, dataset.getPath());
                ps.setInt(4, dataset.getTotalImages());
                ps.setInt(5, dataset.getTotalAnnotations());
                ps.setInt(6, dataset.getTotalClasses());
                ps.setLong(7, dataset.getFileSizeBytes());
                ps.setString(8, dataset.getSha256Hash());
                return ps;
            }, keyHolder);
            if (keyHolder.getKey() != null) {
                dataset.setId(keyHolder.getKey().longValue());
            }
        } else {
            String sql = "UPDATE datasets SET name = ?, format = ?, path = ?, total_images = ?, total_annotations = ?, " +
                    "total_classes = ?, file_size_bytes = ?, sha256_hash = ?, updated_at = datetime('now') WHERE id = ?";
            jdbcTemplate.update(sql,
                    dataset.getName(),
                    dataset.getFormat().name(),
                    dataset.getPath(),
                    dataset.getTotalImages(),
                    dataset.getTotalAnnotations(),
                    dataset.getTotalClasses(),
                    dataset.getFileSizeBytes(),
                    dataset.getSha256Hash(),
                    dataset.getId());
        }
        return dataset;
    }

    public Optional<Dataset> findById(Long id) {
        String sql = "SELECT * FROM datasets WHERE id = ?";
        List<Dataset> list = jdbcTemplate.query(sql, rowMapper, id);
        return list.stream().findFirst();
    }

    public List<Dataset> findAll() {
        String sql = "SELECT * FROM datasets ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    public long count() {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM datasets", Long.class);
        return c != null ? c : 0;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM dataset_findings WHERE dataset_id = ?", id);
        jdbcTemplate.update("DELETE FROM dataset_audits WHERE dataset_id = ?", id);
        jdbcTemplate.update("DELETE FROM datasets WHERE id = ?", id);
    }
}
