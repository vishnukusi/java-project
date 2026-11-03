package com.sentinelcv.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Application-wide configuration.
 * Initializes required directories and thread pools for async audit processing.
 */
@Configuration
public class ApplicationConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationConfig.class);

    @Value("${sentinelcv.data.dir:./data}")
    private String dataDir;

    @Value("${sentinelcv.upload.dir:./data/uploads}")
    private String uploadDir;

    @Value("${sentinelcv.reports.dir:./data/reports}")
    private String reportsDir;

    @Value("${sentinelcv.temp.dir:./data/temp}")
    private String tempDir;

    @Value("${sentinelcv.models.dir:./data/models}")
    private String modelsDir;

    @Value("${sentinelcv.datasets.dir:./data/datasets}")
    private String datasetsDir;

    @Value("${sentinelcv.audit.thread-pool-size:4}")
    private int threadPoolSize;

    @PostConstruct
    public void initDirectories() {
        createDirectory(uploadDir);
        createDirectory(reportsDir);
        createDirectory(tempDir);
        createDirectory(modelsDir);
        createDirectory(datasetsDir);
        logger.info("SentinelCV directories initialized");
    }

    private void createDirectory(String dir) {
        try {
            Path path = Paths.get(dir);
            Files.createDirectories(path);
            logger.debug("Directory ensured: {}", path.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to create directory: {}", dir, e);
        }
    }

    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(threadPoolSize);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("AuditWorker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        logger.info("Audit task executor initialized with max {} threads and AbortPolicy for saturation handling", threadPoolSize);
        return executor;
    }

    public String getDataDir() { return dataDir; }
    public String getUploadDir() { return uploadDir; }
    public String getReportsDir() { return reportsDir; }
    public String getTempDir() { return tempDir; }
    public String getModelsDir() { return modelsDir; }
    public String getDatasetsDir() { return datasetsDir; }
}
