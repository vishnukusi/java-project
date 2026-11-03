package com.sentinelcv.service;

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
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AsyncAuditExecutionTest {

    @Autowired
    private DatasetAuditService datasetAuditService;

    @Autowired
    private ModelAuditService modelAuditService;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetAuditRepository datasetAuditRepository;

    @Autowired
    private MlModelRepository modelRepository;

    @Autowired
    private ModelAuditRepository modelAuditRepository;

    @Test
    void testAsyncDatasetAuditExecution(@TempDir Path tempDir) throws Exception {
        File cocoDir = FixtureGenerator.createSampleCocoDataset(tempDir, "async_coco_dataset");
        Dataset dataset = new Dataset("Async COCO Test", DatasetFormat.COCO, cocoDir.getAbsolutePath());
        dataset.setSha256Hash(HashUtil.sha256("async_coco"));
        dataset = datasetRepository.save(dataset);

        try {
            DatasetAudit audit = new DatasetAudit(dataset.getId());
            audit.setStatus(AuditStatus.PENDING);
            audit = datasetAuditRepository.save(audit);

            // Launch async audit
            CompletableFuture<DatasetAudit> future = datasetAuditService.executeAuditAsync(audit.getId());
            assertNotNull(future);

            DatasetAudit completed = future.get(10, TimeUnit.SECONDS);
            assertNotNull(completed);
            assertEquals(AuditStatus.COMPLETED, completed.getStatus());
            assertNotNull(completed.getAssessmentDecision());
        } finally {
            if (dataset.getId() != null) {
                datasetRepository.deleteById(dataset.getId());
            }
        }
    }

    @Test
    void testAsyncModelAuditExecution(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("async_model.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        MlModel model = new MlModel("Async ONNX Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model.setFileSizeBytes(onnxFile.length());
        model = modelRepository.save(model);

        try {
            ModelAudit audit = new ModelAudit(model.getId());
            audit.setStatus(AuditStatus.PENDING);
            audit = modelAuditRepository.save(audit);

            // Launch async audit
            CompletableFuture<ModelAudit> future = modelAuditService.executeAuditAsync(audit.getId());
            assertNotNull(future);

            ModelAudit completed = future.get(10, TimeUnit.SECONDS);
            assertNotNull(completed);
            assertEquals(AuditStatus.COMPLETED, completed.getStatus());
            assertNotNull(completed.getAssessmentDecision());
        } finally {
            if (model.getId() != null) {
                modelRepository.deleteById(model.getId());
            }
        }
    }
}
