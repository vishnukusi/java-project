package com.sentinelcv.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Database configuration for SQLite.
 * Handles DataSource creation and schema initialization via ResourceDatabasePopulator.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${sentinelcv.data.dir:./data}")
    private String dataDir;

    @Bean
    public DataSource dataSource() {
        Path dataDirPath = Paths.get(dataDir);
        try {
            Files.createDirectories(dataDirPath);
        } catch (IOException e) {
            logger.error("Failed to create data directory: {}", dataDirPath, e);
            throw new RuntimeException("Cannot create data directory", e);
        }

        String dbPath = dataDirPath.resolve("sentinelcv.db").toAbsolutePath().toString();
        logger.info("Initializing SQLite database at: {}", dbPath);

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        initializeSchema(dataSource);
        return jdbcTemplate;
    }

    private void initializeSchema(DataSource dataSource) {
        try {
            logger.info("Executing SQLite schema script...");
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("db/schema.sql"));
            populator.setContinueOnError(true);
            populator.setIgnoreFailedDrops(true);
            populator.execute(dataSource);
            logger.info("Database schema initialized successfully via ResourceDatabasePopulator");
        } catch (Exception e) {
            logger.error("Failed to initialize database schema", e);
            throw new RuntimeException("Database schema initialization failed", e);
        }
    }
}
