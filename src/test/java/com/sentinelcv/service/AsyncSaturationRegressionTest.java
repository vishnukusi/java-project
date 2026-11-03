package com.sentinelcv.service;

import com.sentinelcv.config.ApplicationConfig;
import com.sentinelcv.controller.DatasetController;
import com.sentinelcv.controller.ModelController;
import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DatasetAudit;
import com.sentinelcv.entity.MlModel;
import com.sentinelcv.entity.ModelAudit;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.util.FixtureGenerator;
import com.sentinelcv.util.HashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AsyncSaturationRegressionTest {

    @Autowired
    @Qualifier("auditTaskExecutor")
    private Executor auditTaskExecutor;

    @Autowired
    private ModelController modelController;

    @Autowired
    private DatasetController datasetController;

    @Autowired
    private MlModelRepository modelRepository;

    @Autowired
    private ModelAuditRepository modelAuditRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetAuditRepository datasetAuditRepository;

    @Test
    void testAuditExecutorConfiguredWithAbortPolicyNotCallerRuns() {
        assertTrue(auditTaskExecutor instanceof ThreadPoolTaskExecutor,
                "auditTaskExecutor should be an instance of ThreadPoolTaskExecutor");
        ThreadPoolTaskExecutor threadPoolExecutor = (ThreadPoolTaskExecutor) auditTaskExecutor;

        // AbortPolicy is standard java.util.concurrent.ThreadPoolExecutor.AbortPolicy
        assertTrue(threadPoolExecutor.getThreadPoolExecutor().getRejectedExecutionHandler()
                        instanceof java.util.concurrent.ThreadPoolExecutor.AbortPolicy,
                "Executor MUST use AbortPolicy so long-running audits never hijack HTTP caller threads");
    }

    @Test
    void testDuplicateConcurrentModelAuditSubmissionBlocked(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("duplicate_check_model.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        MlModel model = new MlModel("Duplicate Test Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model = modelRepository.save(model);

        // Simulate an ongoing audit with status RUNNING
        ModelAudit activeAudit = new ModelAudit(model.getId());
        activeAudit.setStatus(AuditStatus.RUNNING);
        activeAudit = modelAuditRepository.save(activeAudit);

        // Attempt second concurrent submission via Controller
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = modelController.startAudit(model.getId(), redirectAttributes);

        assertEquals("redirect:/models/" + model.getId(), view);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("error"),
                "Controller must reject duplicate audit submission with error flash attribute");
        assertTrue(redirectAttributes.getFlashAttributes().get("error").toString().contains("already running or pending"),
                "Error message must inform user that an audit is already running");
    }

    @Test
    void testDuplicateConcurrentDatasetAuditSubmissionBlocked(@TempDir Path tempDir) throws Exception {
        File cocoDir = FixtureGenerator.createSampleCocoDataset(tempDir, "dup_ds_check");
        Dataset dataset = new Dataset("Duplicate Dataset Test", DatasetFormat.COCO, cocoDir.getAbsolutePath());
        dataset.setSha256Hash(HashUtil.sha256("dup_ds_check"));
        dataset = datasetRepository.save(dataset);

        DatasetAudit activeAudit = new DatasetAudit(dataset.getId());
        activeAudit.setStatus(AuditStatus.PENDING);
        activeAudit = datasetAuditRepository.save(activeAudit);

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String view = datasetController.startAudit(dataset.getId(), redirectAttributes);

        assertEquals("redirect:/datasets/" + dataset.getId(), view);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("error"));
        assertTrue(redirectAttributes.getFlashAttributes().get("error").toString().contains("already running or pending"));
    }

    @Test
    void testExecutorSaturationThrowsExceptionInsteadOfRunningOnCallerThread() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) auditTaskExecutor;
        int maxPool = executor.getMaxPoolSize();
        int queueCap = executor.getQueueCapacity();
        int totalCapacity = maxPool + queueCap;

        CountDownLatch blockerLatch = new CountDownLatch(1);

        try {
            // Submit tasks to fill up pool and queue capacity
            for (int i = 0; i < totalCapacity; i++) {
                executor.execute(() -> {
                    try {
                        blockerLatch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {}
                });
            }

            // Next submission MUST be aborted immediately (AbortPolicy), throwing exception
            assertThrows(RejectedExecutionException.class, () -> {
                executor.execute(() -> {
                    // This task should never execute
                });
            }, "Saturated executor must reject task with RejectedExecutionException rather than running on caller thread");

        } finally {
            // Release blocked tasks
            blockerLatch.countDown();
        }
    }
}
