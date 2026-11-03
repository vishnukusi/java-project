package com.sentinelcv.config;

import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.InferenceRecord;
import com.sentinelcv.entity.MlModel;
import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.inference.InferenceValidator;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.DriftAuditRepository;
import com.sentinelcv.repository.InferenceRecordRepository;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.util.CryptoUtil;
import com.sentinelcv.util.FixtureGenerator;
import com.sentinelcv.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * Initializes default sample datasets, demo models, and hash-linked inference records
 * on first startup for demonstration and immediate offline operation.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final DatasetRepository datasetRepository;
    private final MlModelRepository modelRepository;
    private final InferenceRecordRepository recordRepository;
    private final DatasetAuditRepository datasetAuditRepository;
    private final ModelAuditRepository modelAuditRepository;
    private final DriftAuditRepository driftAuditRepository;
    private final ApplicationConfig applicationConfig;
    private final InferenceValidator inferenceValidator;
    private final com.sentinelcv.provenance.ProvenanceKeyManager provenanceKeyManager;
    private final com.sentinelcv.service.RiskAssessmentService riskAssessmentService;

    public DataInitializer(DatasetRepository datasetRepository,
                           MlModelRepository modelRepository,
                           InferenceRecordRepository recordRepository,
                           DatasetAuditRepository datasetAuditRepository,
                           ModelAuditRepository modelAuditRepository,
                           DriftAuditRepository driftAuditRepository,
                           ApplicationConfig applicationConfig,
                           InferenceValidator inferenceValidator,
                           com.sentinelcv.provenance.ProvenanceKeyManager provenanceKeyManager,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) com.sentinelcv.service.RiskAssessmentService riskAssessmentService) {
        this.datasetRepository = datasetRepository;
        this.modelRepository = modelRepository;
        this.recordRepository = recordRepository;
        this.datasetAuditRepository = datasetAuditRepository;
        this.modelAuditRepository = modelAuditRepository;
        this.driftAuditRepository = driftAuditRepository;
        this.applicationConfig = applicationConfig;
        this.inferenceValidator = inferenceValidator;
        this.provenanceKeyManager = provenanceKeyManager;
        this.riskAssessmentService = riskAssessmentService;
    }

    @Override
    public void run(String... args) throws Exception {
        cleanupInterruptedAudits();
        initializeSampleDatasets();
        initializeSampleModels();
        initializeSampleInferenceChain();
    }

    private void cleanupInterruptedAudits() {
        try {
            int cleanedDatasets = datasetAuditRepository.cleanupInterruptedAudits();
            int cleanedModels = modelAuditRepository.cleanupInterruptedAudits();
            int cleanedDrifts = driftAuditRepository.cleanupInterruptedAudits();
            int total = cleanedDatasets + cleanedModels + cleanedDrifts;
            if (total > 0) {
                logger.warn("Recovered from ungraceful shutdown/restart: marked {} interrupted audit(s) as FAILED (datasets={}, models={}, drift={})",
                        total, cleanedDatasets, cleanedModels, cleanedDrifts);
            }
            if (riskAssessmentService != null) {
                riskAssessmentService.recoverInterruptedAuditsAndInvalidate();
            }
        } catch (Exception e) {
            logger.warn("Could not clean up interrupted audits: {}", e.getMessage());
        }
    }

    private void initializeSampleDatasets() {
        if (datasetRepository.count() > 0) {
            return;
        }

        try {
            Path datasetsBase = Paths.get(applicationConfig.getDatasetsDir());
            // Create Sample COCO
            File cocoDir = FixtureGenerator.createSampleCocoDataset(datasetsBase, "demo_coco_dataset");
            Dataset coco = new Dataset("COCO Benchmark Sample", DatasetFormat.COCO, cocoDir.getAbsolutePath());
            coco.setTotalImages(3);
            coco.setTotalAnnotations(3);
            coco.setTotalClasses(2);
            coco.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(cocoDir));
            datasetRepository.save(coco);

            // Create Sample YOLO
            File yoloDir = FixtureGenerator.createSampleYoloDataset(datasetsBase, "demo_yolo_dataset");
            Dataset yolo = new Dataset("YOLO Traffic Sample", DatasetFormat.YOLO, yoloDir.getAbsolutePath());
            yolo.setTotalImages(2);
            yolo.setTotalAnnotations(2);
            yolo.setTotalClasses(2);
            yolo.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(yoloDir));
            datasetRepository.save(yolo);

            logger.info("Initialized sample COCO and YOLO datasets for offline demonstration.");
        } catch (Exception e) {
            logger.warn("Sample dataset initialization skipped: {}", e.getMessage());
        }
    }

    private void initializeSampleModels() {
        if (modelRepository.count() > 0) {
            return;
        }

        try {
            Path modelsBase = Paths.get(applicationConfig.getModelsDir());
            Files.createDirectories(modelsBase);
            File realModel = modelsBase.resolve("demo_vision_model.onnx").toFile();
            if (!realModel.exists() || realModel.length() <= 8) {
                // Generate a genuine, valid ONNX vision model with shape [1, 3, 224, 224] and Relu operator
                FixtureGenerator.createSampleOnnxModel(realModel, new long[]{1, 3, 224, 224}, "Relu");
            }

            MlModel model = new MlModel("Demo Vision Classifier", ModelFormat.ONNX, realModel.getAbsolutePath());
            model.setFileSizeBytes(realModel.length());
            model.setSha256Hash(HashUtil.sha256(realModel));
            model.setInputInfo("[input (FLOAT)[1, 3, 224, 224]]");
            model.setOutputInfo("[output (FLOAT)[1, 3, 224, 224]]");
            modelRepository.save(model);

            logger.info("Initialized genuine valid ONNX model ({} bytes) for offline demonstration.", realModel.length());
        } catch (Exception e) {
            logger.warn("Sample model initialization skipped: {}", e.getMessage());
        }
    }

    private void initializeSampleInferenceChain() {
        if (recordRepository.count() > 0) {
            java.util.List<InferenceRecord> existing = recordRepository.findAllOrderedBySequence();
            if (!existing.isEmpty()) {
                InferenceRecord first = existing.get(0);
                String expected = HashUtil.sha256(inferenceValidator.computeCanonicalPayload(first));
                if (expected.equalsIgnoreCase(first.getCurrentRecordHash())) {
                    return; // Already initialized with current canonical format
                }
            }
            recordRepository.deleteAll(); // Legacy records, re-seed with V2 format
        }

        try {
            String prevHash = "GENESIS";
            String hmacKey = provenanceKeyManager.getOrCreateLocalKey();

            for (int i = 1; i <= 3; i++) {
                InferenceRecord r = new InferenceRecord();
                r.setRecordId("rec_00" + i);
                r.setSequenceNumber(i);
                r.setTimestamp(LocalDateTime.now().minusMinutes(10 - i).toString());
                r.setModelIdentifier("demo_vision_model");
                r.setModelHash(HashUtil.sha256("model_bytes_" + i));
                r.setInputImageHash(HashUtil.sha256("image_bytes_" + i));
                r.setConfigHash(HashUtil.sha256("config_standard"));
                r.setOutputHash(HashUtil.sha256("output_logits_" + i));
                r.setNonce(CryptoUtil.generateNonce());
                r.setPreviousRecordHash(prevHash);

                String payload = inferenceValidator.computeCanonicalPayload(r);
                String currentHash = HashUtil.sha256(payload);
                r.setCurrentRecordHash(currentHash);
                r.setHmacValue(CryptoUtil.computeHmac(payload, hmacKey));

                recordRepository.save(r);
                prevHash = currentHash;
            }

            logger.info("Initialized 3 cryptographically linked sample inference records using secure instance key.");
        } catch (Exception e) {
            logger.warn("Sample inference records initialization skipped: {}", e.getMessage());
        }
    }
}
