package com.sentinelcv.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SettingRepository {

    private final JdbcTemplate jdbcTemplate;

    public SettingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> getValue(String key) {
        String sql = "SELECT value FROM app_settings WHERE key = ?";
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("value"), key);
        return list.stream().findFirst();
    }

    public String getValueOrDefault(String key, String defaultValue) {
        return getValue(key).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return getValue(key).map(Integer::parseInt).orElse(defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        return getValue(key).map(Double::parseDouble).orElse(defaultValue);
    }

    public void setValue(String key, String value) {
        String sql = "INSERT OR REPLACE INTO app_settings (key, value, updated_at) VALUES (?, ?, datetime('now'))";
        jdbcTemplate.update(sql, key, value);
    }

    public Map<String, String> getAll() {
        String sql = "SELECT key, value FROM app_settings";
        Map<String, String> map = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            map.put(rs.getString("key"), rs.getString("value"));
        });
        return map;
    }
}
