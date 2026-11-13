package com.sentinelcv.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelcv.dataset.AnnotationValidator;
import com.sentinelcv.dataset.DatasetModels;
import com.sentinelcv.dataset.DatasetParser;
import com.sentinelcv.dataset.DatasetStatisticsService;
import com.sentinelcv.dataset.DuplicateDetector;
import com.sentinelcv.dataset.ImageIntegrityAnalyzer;
import com.sentinelcv.dataset.YoloParser;
import com.sentinelcv.entity.AuditHistory;
import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DatasetAudit;
import com.sentinelcv.entity.MlModel;
import com.sentinelcv.entity.ModelAudit;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.exception.DatasetParsingException;
import com.sentinelcv.model.BehavioralAnalyzer;
import com.sentinelcv.model.ModelFingerprintService;
import com.sentinelcv.model.ModelLoader;
import com.sentinelcv.model.ModelMetadata;
import com.sentinelcv.model.TriggerAnalyzer;
import com.sentinelcv.repository.AuditHistoryRepository;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetFindingRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.repository.ModelFindingRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.repository.SettingRepository;
import com.sentinelcv.service.DatasetAuditService;
import com.sentinelcv.service.ModelAuditService;
import com.sentinelcv.service.RiskAssessmentService;
import com.sentinelcv.util.ArchiveUtil;
import com.sentinelcv.util.FixtureGenerator;
import com.sentinelcv.util.HashUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AsyncAuditResultLifecycleRegressionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DatasetRepository datasetRepository;
    @Autowired private DatasetAuditRepository datasetAuditRepository;
    @Autowired private DatasetFindingRepository datasetFindingRepository;
    @Autowired private MlModelRepository modelRepository;
    @Autowired private ModelAuditRepository modelAuditRepository;
    @Autowired private ModelFindingRepository modelFindingRepository;
    @Autowired private SecurityFindingRepository securityFindingRepository;
    @Autowired private AuditHistoryRepository auditHistoryRepository;
    @Autowired private SettingRepository settingRepository;
    @Autowired private RiskAssessmentService riskAssessmentService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DuplicateDetector duplicateDetector;
    @Autowired private AnnotationValidator annotationValidator;
    @Autowired private ImageIntegrityAnalyzer imageIntegrityAnalyzer;
    @Autowired private DatasetStatisticsService statisticsService;
    @Autowired private ModelFingerprintService fingerprintService;
    @Autowired private BehavioralAnalyzer behavioralAnalyzer;
    @Autowired private TriggerAnalyzer triggerAnalyzer;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void testRunningAndPendingDatasetAuditPagesNeverRenderFakeZerosOrCleanMessages(@TempDir Path tempDir) throws Exception {
        File yoloDir = FixtureGenerator.createSampleYoloDataset(tempDir, "running_view_yolo");
        Dataset dataset = new Dataset("Running View Dataset", DatasetFormat.YOLO, yoloDir.getAbsolutePath());
        dataset.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(yoloDir));
        dataset = datasetRepository.save(dataset);

        try {
            DatasetAudit running = new DatasetAudit(dataset.getId());
            running.setStatus(AuditStatus.RUNNING);
            running.setStartedAt(LocalDateTime.now());
            running.setIntegrityScore(0.0);
            running.setTotalFindings(0);
            running.setCriticalFindings(0);
            running = datasetAuditRepository.save(running);

            mockMvc.perform(get("/datasets/" + dataset.getId() + "/audits/" + running.getId()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(content().string(containsString("data-initial-status=\"RUNNING\"")))
                    .andExpect(content().string(containsString("Analysis in progress")))
                    .andExpect(content().string(containsString("scv-spinner")))
                    .andExpect(content().string(containsString("/static/js/audit_detail.js")))
                    .andExpect(content().string(containsString("/datasets/" + dataset.getId() + "/audits/" + running.getId() + "/status")))
                    .andExpect(content().string(not(containsString("Completed: null"))))
                    .andExpect(content().string(not(containsString("0.0%"))))
                    .andExpect(content().string(not(containsString("No summary generated."))))
                    .andExpect(content().string(not(containsString("No security vulnerabilities or anomalies flagged"))));

            mockMvc.perform(get("/datasets/" + dataset.getId() + "/audits/" + running.getId() + "/status"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.auditId").value(running.getId()))
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.decision").doesNotExist())
                    .andExpect(jsonPath("$.integrityScore").doesNotExist());

            DatasetAudit pending = new DatasetAudit(dataset.getId());
            pending.setStatus(AuditStatus.PENDING);
            pending = datasetAuditRepository.save(pending);

            mockMvc.perform(get("/datasets/" + dataset.getId() + "/audits/" + pending.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-initial-status=\"PENDING\"")))
                    .andExpect(content().string(containsString("Analysis in progress")))
                    .andExpect(content().string(not(containsString("0.0%"))))
                    .andExpect(content().string(not(containsString("No security vulnerabilities or anomalies flagged"))));
        } finally {
            datasetRepository.deleteById(dataset.getId());
        }
    }

    @Test
    void testAsyncTransitionFromRunningToCompletedUpdatesStatusAndRendersPersistedFindingsAndEvidence(@TempDir Path tempDir) throws Exception {
        File yoloDir = FixtureGenerator.createSampleYoloDataset(tempDir, "transition_yolo");
        Dataset dataset = new Dataset("Async Transition YOLO", DatasetFormat.YOLO, yoloDir.getAbsolutePath());
        dataset.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(yoloDir));
        dataset = datasetRepository.save(dataset);

        CountDownLatch enteredParser = new CountDownLatch(1);
        CountDownLatch releaseParser = new CountDownLatch(1);

        YoloParser realYoloParser = new YoloParser();
        DatasetParser gatingYoloParser = new DatasetParser() {
            @Override
            public DatasetFormat getFormat() {
                return DatasetFormat.YOLO;
            }

            @Override
            public boolean canParse(File dir) {
                return true;
            }

            @Override
            public DatasetModels.ParseResult parse(File dir) throws DatasetParsingException {
                enteredParser.countDown();
                try {
                    assertTrue(releaseParser.await(10, TimeUnit.SECONDS), "Timed out waiting to release parser");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DatasetParsingException("Interrupted waiting on latch", e);
                }
                return realYoloParser.parse(dir);
            }
        };

        DatasetAuditService gatedService = new DatasetAuditService(
                List.of(gatingYoloParser),
                duplicateDetector,
                annotationValidator,
                imageIntegrityAnalyzer,
                statisticsService,
                datasetRepository,
                datasetAuditRepository,
                datasetFindingRepository,
                securityFindingRepository,
                auditHistoryRepository,
                settingRepository,
                riskAssessmentService
        );
        ReflectionTestUtils.setField(gatedService, "transactionManager", transactionManager);

        try {
            DatasetAudit audit = new DatasetAudit(dataset.getId());
            audit.setStatus(AuditStatus.PENDING);
            audit = datasetAuditRepository.save(audit);
            final Long auditId = audit.getId();

            CompletableFuture<DatasetAudit> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return gatedService.executeAuditAsync(auditId).join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            assertTrue(enteredParser.await(10, TimeUnit.SECONDS), "Worker did not transition into parser execution");

            // While worker is still RUNNING inside parser, verify status endpoint and HTML view
            mockMvc.perform(get("/datasets/" + dataset.getId() + "/audits/" + auditId + "/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.integrityScore").doesNotExist());

            mockMvc.perform(get("/datasets/" + dataset.getId() + "/audits/" + auditId))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-initial-status=\"RUNNING\"")))
                    .andExpect(content().string(containsString("Analysis in progress")))
                    .andExpect(content().string(not(containsString("0.0%"))))
                    .andExpect(content().string(not(containsString("No security vulnerabilities"))));

            // Release worker to finish analysis and persist findings atomically
            releaseParser.countDown();
            DatasetAudit finished = future.get(15, TimeUnit.SECONDS);
            assertEquals(AuditStatus.COMPLETED, finished.getStatus());
            assertNotNull(finished.getCompletedAt());
            assertTrue(finished.getTotalFindings() > 0, "Sample YOLO dataset should emit at least contributor provenance INFO finding");

            // Verify status endpoint reflects terminal persisted state immediately
            mockMvc.perform(get("/datasets/" + dataset.getId() + "/audits/" + auditId + "/status"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.decision").value(finished.getAssessmentDecision().name()))
                    .andExpect(jsonPath("$.totalFindings").value(finished.getTotalFindings()))
                    .andExpect(jsonPath("$.integrityScore").value(finished.getIntegrityScore()));

            // Verify refreshed HTML page renders completed metrics, summary, findings, and evidence
            List<SecurityFinding> persistedFindings = datasetFindingRepository.findByAuditId(auditId);
            assertEquals(finished.getTotalFindings(), persistedFindings.size());
            String firstEvidence = persistedFindings.get(0).getEvidence();

            mockMvc.perform(get("/datasets/" + dataset.getId() + "/audits/" + auditId))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-initial-status=\"COMPLETED\"")))
                    .andExpect(content().string(containsString(finished.getAssessmentDecision().name())))
                    .andExpect(content().string(containsString(finished.getSummary())))
                    .andExpect(content().string(containsString(persistedFindings.get(0).getCategory())))
                    .andExpect(content().string(containsString(firstEvidence)))
                    .andExpect(content().string(not(containsString("Analysis in progress"))));
        } finally {
            datasetRepository.deleteById(dataset.getId());
            cleanupAuditHistory("Async Transition YOLO");
        }
    }

    @Test
    void testFailedAndCancelledAuditsRenderRealFailureWithoutCleanAssessmentOrZeros(@TempDir Path tempDir) throws Exception {
        File yoloDir = FixtureGenerator.createSampleYoloDataset(tempDir, "failed_view_yolo");
        Dataset dataset = new Dataset("Failed Audit Display Test", DatasetFormat.YOLO, yoloDir.getAbsolutePath());
        dataset.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(yoloDir));
        dataset = datasetRepository.save(dataset);

        try {
            DatasetAudit failed = new DatasetAudit(dataset.getId());
            failed.setStatus(AuditStatus.FAILED);
            failed.setStartedAt(LocalDateTime.now().minusSeconds(5));
            failed.setCompletedAt(LocalDateTime.now());
            failed.setErrorMessage("Simulated parser failure: malformed bounding box coordinates");
            failed = datasetAuditRepository.save(failed);

            mockMvc.perform(get("/datasets/" + dataset.getId() + "/audits/" + failed.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-initial-status=\"FAILED\"")))
                    .andExpect(content().string(containsString("Audit Failed:")))
                    .andExpect(content().string(containsString("Simulated parser failure: malformed bounding box coordinates")))
                    .andExpect(content().string(not(containsString("0.0%"))))
                    .andExpect(content().string(not(containsString("ACCEPT"))))
                    .andExpect(content().string(not(containsString("No security vulnerabilities"))));

            DatasetAudit cancelled = new DatasetAudit(dataset.getId());
            cancelled.setStatus(AuditStatus.CANCELLED);
            cancelled.setStartedAt(LocalDateTime.now().minusSeconds(5));
            cancelled.setCompletedAt(LocalDateTime.now());
            cancelled = datasetAuditRepository.save(cancelled);

            mockMvc.perform(get("/datasets/" + dataset.getId() + "/audits/" + cancelled.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-initial-status=\"CANCELLED\"")))
                    .andExpect(content().string(containsString("Audit Cancelled")))
                    .andExpect(content().string(not(containsString("0.0%"))))
                    .andExpect(content().string(not(containsString("No security vulnerabilities"))));
        } finally {
            datasetRepository.deleteById(dataset.getId());
        }
    }

    @Test
    void testExceptionsAndErrorsNeverLeaveDatasetOrModelAuditStuckRunning(@TempDir Path tempDir) throws Exception {
        File yoloDir = FixtureGenerator.createSampleYoloDataset(tempDir, "boom_yolo");
        Dataset dataset = new Dataset("Failing Worker Dataset", DatasetFormat.YOLO, yoloDir.getAbsolutePath());
        dataset.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(yoloDir));
        dataset = datasetRepository.save(dataset);

        File onnxFile = tempDir.resolve("boom_model.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile);
        MlModel model = new MlModel("Failing Worker Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model.setFileSizeBytes(onnxFile.length());
        model = modelRepository.save(model);

        try {
            // 1. Dataset parser throws RuntimeException
            DatasetParser throwingParser = new DatasetParser() {
                @Override public DatasetFormat getFormat() { return DatasetFormat.YOLO; }
                @Override public boolean canParse(File dir) { return true; }
                @Override public DatasetModels.ParseResult parse(File dir) {
                    throw new IllegalStateException("Corrupt YAML header in dataset");
                }
            };
            DatasetAuditService exDatasetService = new DatasetAuditService(
                    List.of(throwingParser), duplicateDetector, annotationValidator, imageIntegrityAnalyzer,
                    statisticsService, datasetRepository, datasetAuditRepository, datasetFindingRepository,
                    securityFindingRepository, auditHistoryRepository, settingRepository, riskAssessmentService);
            ReflectionTestUtils.setField(exDatasetService, "transactionManager", transactionManager);

            DatasetAudit dsExAudit = datasetAuditRepository.save(new DatasetAudit(dataset.getId()));
            DatasetAudit dsExResult = exDatasetService.executeAuditAsync(dsExAudit.getId()).get(10, TimeUnit.SECONDS);
            assertEquals(AuditStatus.FAILED, dsExResult.getStatus());
            assertNotNull(dsExResult.getCompletedAt());
            assertTrue(dsExResult.getErrorMessage().contains("Corrupt YAML header in dataset"));

            DatasetAudit persistedDsEx = datasetAuditRepository.findById(dsExAudit.getId()).orElseThrow();
            assertEquals(AuditStatus.FAILED, persistedDsEx.getStatus());
            assertNotNull(persistedDsEx.getCompletedAt());

            // 2. Dataset worker throws java.lang.Error (must still transition DB record off RUNNING!)
            DatasetParser errorParser = new DatasetParser() {
                @Override public DatasetFormat getFormat() { return DatasetFormat.YOLO; }
                @Override public boolean canParse(File dir) { return true; }
                @Override public DatasetModels.ParseResult parse(File dir) {
                    throw new AssertionError("Simulated fatal native error");
                }
            };
            DatasetAuditService errDatasetService = new DatasetAuditService(
                    List.of(errorParser), duplicateDetector, annotationValidator, imageIntegrityAnalyzer,
                    statisticsService, datasetRepository, datasetAuditRepository, datasetFindingRepository,
                    securityFindingRepository, auditHistoryRepository, settingRepository, riskAssessmentService);

            DatasetAudit dsErrAudit = datasetAuditRepository.save(new DatasetAudit(dataset.getId()));
            assertThrows(AssertionError.class, () -> errDatasetService.executeAudit(dsErrAudit.getId()));
            DatasetAudit persistedDsErr = datasetAuditRepository.findById(dsErrAudit.getId()).orElseThrow();
            assertEquals(AuditStatus.FAILED, persistedDsErr.getStatus());
            assertNotNull(persistedDsErr.getCompletedAt());
            assertTrue(persistedDsErr.getErrorMessage().contains("Simulated fatal native error"));

            // 3. Transaction rollback guard: if history persistence fails after findings insert, findings + COMPLETED roll back atomically -> FAILED
            AuditHistoryRepository failingHistoryRepo = new AuditHistoryRepository(jdbcTemplate) {
                @Override
                public AuditHistory save(AuditHistory history) {
                    throw new RuntimeException("Simulated audit_history write failure");
                }
            };
            DatasetAuditService txRollbackService = new DatasetAuditService(
                    List.of(new YoloParser()), duplicateDetector, annotationValidator, imageIntegrityAnalyzer,
                    statisticsService, datasetRepository, datasetAuditRepository, datasetFindingRepository,
                    securityFindingRepository, failingHistoryRepo, settingRepository, riskAssessmentService);
            ReflectionTestUtils.setField(txRollbackService, "transactionManager", transactionManager);

            DatasetAudit dsTxAudit = datasetAuditRepository.save(new DatasetAudit(dataset.getId()));
            DatasetAudit dsTxResult = txRollbackService.executeAudit(dsTxAudit.getId());
            assertEquals(AuditStatus.FAILED, dsTxResult.getStatus());
            assertTrue(datasetFindingRepository.findByAuditId(dsTxAudit.getId()).isEmpty(),
                    "Findings must roll back atomically if completion commit fails");

            // 4. Model loader throws RuntimeException -> ModelAudit must transition to FAILED cleanly
            ModelLoader throwingLoader = new ModelLoader() {
                @Override public boolean canLoad(File f) { return true; }
                @Override public ModelMetadata inspectMetadata(File f) {
                    throw new RuntimeException("Simulated ONNX parse crash");
                }
                @Override public float[] runInference(File f, float[] in, long[] shape) { return new float[0]; }
                @Override public ModelFormat getFormat() { return ModelFormat.ONNX; }
            };
            ModelAuditService exModelService = new ModelAuditService(
                    List.of(throwingLoader), fingerprintService, behavioralAnalyzer, triggerAnalyzer,
                    modelRepository, modelAuditRepository, modelFindingRepository, securityFindingRepository,
                    auditHistoryRepository, riskAssessmentService, objectMapper);
            ReflectionTestUtils.setField(exModelService, "transactionManager", transactionManager);

            ModelAudit modelExAudit = modelAuditRepository.save(new ModelAudit(model.getId()));
            ModelAudit modelExResult = exModelService.executeAuditAsync(modelExAudit.getId()).get(10, TimeUnit.SECONDS);
            assertEquals(AuditStatus.FAILED, modelExResult.getStatus());
            assertNotNull(modelExResult.getCompletedAt());
            assertTrue(modelExResult.getErrorMessage().contains("Simulated ONNX parse crash"));
            assertEquals(AuditStatus.FAILED, modelAuditRepository.findById(modelExAudit.getId()).orElseThrow().getStatus());

            // 5. Model loader throws java.lang.Error in async worker -> must mark FAILED cleanly
            ModelLoader errorLoader = new ModelLoader() {
                @Override public boolean canLoad(File f) { return true; }
                @Override public ModelMetadata inspectMetadata(File f) {
                    throw new AssertionError("Simulated native JNI linkage error");
                }
                @Override public float[] runInference(File f, float[] in, long[] shape) { return new float[0]; }
                @Override public ModelFormat getFormat() { return ModelFormat.ONNX; }
            };
            ModelAuditService errModelService = new ModelAuditService(
                    List.of(errorLoader), fingerprintService, behavioralAnalyzer, triggerAnalyzer,
                    modelRepository, modelAuditRepository, modelFindingRepository, securityFindingRepository,
                    auditHistoryRepository, riskAssessmentService, objectMapper);
            ModelAudit modelErrAudit = modelAuditRepository.save(new ModelAudit(model.getId()));
            CompletableFuture<ModelAudit> errFuture = errModelService.executeAuditAsync(modelErrAudit.getId());
            assertThrows(Exception.class, () -> errFuture.get(10, TimeUnit.SECONDS));
            ModelAudit persistedModelErr = modelAuditRepository.findById(modelErrAudit.getId()).orElseThrow();
            assertEquals(AuditStatus.FAILED, persistedModelErr.getStatus());
            assertNotNull(persistedModelErr.getCompletedAt());
            assertTrue(persistedModelErr.getErrorMessage().contains("Simulated native JNI linkage error"));
        } finally {
            datasetRepository.deleteById(dataset.getId());
            modelRepository.deleteById(model.getId());
            cleanupAuditHistory("Failing Worker Dataset");
        }
    }

    @Test
    void testRealAsyncModelAndDatasetUploadsCompleteAndRenderViaEndpoints(@TempDir Path tempDir) throws Exception {
        // 1. Real YOLO HTTP upload + async audit + status poll + detail view
        File yoloDir = FixtureGenerator.createSampleYoloDataset(tempDir, "http_yolo");
        File yoloZip = FixtureGenerator.zipDirectory(yoloDir, tempDir.resolve("http_yolo.zip").toFile());
        MockMultipartFile yoloFile = new MockMultipartFile(
                "archive", "http_yolo.zip", "application/zip", Files.readAllBytes(yoloZip.toPath()));

        Long yoloDatasetId = uploadDatasetAndGetId("Lifecycle YOLO Upload", "YOLO", yoloFile);
        // Also test COCO upload end-to-end
        File cocoDir = FixtureGenerator.createSampleCocoDataset(tempDir, "http_coco");
        File cocoZip = FixtureGenerator.zipDirectory(cocoDir, tempDir.resolve("http_coco.zip").toFile());
        MockMultipartFile cocoFile = new MockMultipartFile(
                "archive", "http_coco.zip", "application/zip", Files.readAllBytes(cocoZip.toPath()));
        Long cocoDatasetId = uploadDatasetAndGetId("Lifecycle COCO Upload", "COCO", cocoFile);

        // 2. Real ONNX model upload + async audit + status poll + detail view
        File onnxFile = tempDir.resolve("lifecycle_model.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 32, 32}, "Relu");
        MockMultipartFile modelUpload = new MockMultipartFile(
                "modelFile", "lifecycle_model.onnx", MediaType.APPLICATION_OCTET_STREAM_VALUE, Files.readAllBytes(onnxFile.toPath()));

        Long modelId = null;
        try {
            MvcResult modelUpRes = mockMvc.perform(multipart("/models/upload")
                            .file(modelUpload)
                            .param("name", "Lifecycle ONNX Model")
                            .param("format", "ONNX"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            modelId = extractTrailingId(modelUpRes.getResponse().getRedirectedUrl(), "/models/");

            // Trigger YOLO dataset audit asynchronously via HTTP POST
            MvcResult yoloAuditStart = mockMvc.perform(post("/datasets/" + yoloDatasetId + "/audit"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            Long yoloAuditId = extractTrailingId(yoloAuditStart.getResponse().getRedirectedUrl(), "/datasets/" + yoloDatasetId + "/audits/");

            // Trigger COCO dataset audit asynchronously via HTTP POST
            MvcResult cocoAuditStart = mockMvc.perform(post("/datasets/" + cocoDatasetId + "/audit"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            Long cocoAuditId = extractTrailingId(cocoAuditStart.getResponse().getRedirectedUrl(), "/datasets/" + cocoDatasetId + "/audits/");

            // Trigger ONNX model audit asynchronously via HTTP POST
            MvcResult modelAuditStart = mockMvc.perform(post("/models/" + modelId + "/audit"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            Long modelAuditId = extractTrailingId(modelAuditStart.getResponse().getRedirectedUrl(), "/models/" + modelId + "/audits/");

            DatasetAudit completedYolo = awaitDatasetAuditTerminal(yoloAuditId);
            DatasetAudit completedCoco = awaitDatasetAuditTerminal(cocoAuditId);
            ModelAudit completedModel = awaitModelAuditTerminal(modelAuditId);

            assertEquals(AuditStatus.COMPLETED, completedYolo.getStatus());
            assertEquals(AuditStatus.COMPLETED, completedCoco.getStatus());
            assertEquals(AuditStatus.COMPLETED, completedModel.getStatus());

            Dataset persistedYolo = datasetRepository.findById(yoloDatasetId).orElseThrow();
            assertEquals(DatasetFormat.YOLO, persistedYolo.getFormat());
            assertEquals(2, persistedYolo.getTotalImages());
            assertEquals(2, persistedYolo.getTotalAnnotations());

            Dataset persistedCoco = datasetRepository.findById(cocoDatasetId).orElseThrow();
            assertEquals(DatasetFormat.COCO, persistedCoco.getFormat());
            assertEquals(3, persistedCoco.getTotalImages());
            assertEquals(3, persistedCoco.getTotalAnnotations());

            // Model audit detail view & status contract check
            mockMvc.perform(get("/models/" + modelId + "/audits/" + modelAuditId + "/status"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.decision").value(completedModel.getAssessmentDecision().name()));

            mockMvc.perform(get("/models/" + modelId + "/audits/" + modelAuditId))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-initial-status=\"COMPLETED\"")))
                    .andExpect(content().string(containsString(completedModel.getAssessmentDecision().name())))
                    .andExpect(content().string(containsString(completedModel.getSummary())))
                    .andExpect(content().string(not(containsString("Analysis in progress"))));

            // Verify RUNNING state on model audit page as well
            ModelAudit runningModelAudit = new ModelAudit(modelId);
            runningModelAudit.setStatus(AuditStatus.RUNNING);
            runningModelAudit.setStartedAt(LocalDateTime.now());
            runningModelAudit = modelAuditRepository.save(runningModelAudit);

            mockMvc.perform(get("/models/" + modelId + "/audits/" + runningModelAudit.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-initial-status=\"RUNNING\"")))
                    .andExpect(content().string(containsString("Analysis in progress")))
                    .andExpect(content().string(not(containsString("Completed: null"))))
                    .andExpect(content().string(not(containsString("No security vulnerabilities"))));
        } finally {
            datasetRepository.findById(yoloDatasetId).ifPresent(d -> ArchiveUtil.deleteDirectoryRecursively(Path.of(d.getPath())));
            datasetRepository.findById(cocoDatasetId).ifPresent(d -> ArchiveUtil.deleteDirectoryRecursively(Path.of(d.getPath())));
            datasetRepository.deleteById(yoloDatasetId);
            datasetRepository.deleteById(cocoDatasetId);
            if (modelId != null) {
                modelRepository.findById(modelId).ifPresent(m -> {
                    try { Files.deleteIfExists(Path.of(m.getPath())); } catch (Exception ignored) {}
                });
                modelRepository.deleteById(modelId);
            }
            cleanupAuditHistory("Lifecycle YOLO Upload");
            cleanupAuditHistory("Lifecycle COCO Upload");
            cleanupAuditHistory("Lifecycle ONNX Model");
        }
    }

    private Long uploadDatasetAndGetId(String name, String format, MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/datasets/upload")
                        .file(file)
                        .param("name", name)
                        .param("format", format))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return extractTrailingId(result.getResponse().getRedirectedUrl(), "/datasets/");
    }

    private static Long extractTrailingId(String redirectUrl, String prefix) {
        assertNotNull(redirectUrl);
        int idx = redirectUrl.indexOf(prefix);
        assertTrue(idx >= 0, "Unexpected redirect URL: " + redirectUrl);
        String tail = redirectUrl.substring(idx + prefix.length());
        int semi = tail.indexOf(';');
        if (semi >= 0) tail = tail.substring(0, semi);
        return Long.parseLong(tail);
    }

    private DatasetAudit awaitDatasetAuditTerminal(Long auditId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            DatasetAudit a = datasetAuditRepository.findById(auditId).orElseThrow();
            if (a.getStatus() == AuditStatus.COMPLETED || a.getStatus() == AuditStatus.FAILED) {
                return a;
            }
            Thread.sleep(50);
        }
        fail("Timed out waiting for dataset audit " + auditId + " to reach terminal state");
        return null;
    }

    private ModelAudit awaitModelAuditTerminal(Long auditId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            ModelAudit a = modelAuditRepository.findById(auditId).orElseThrow();
            if (a.getStatus() == AuditStatus.COMPLETED || a.getStatus() == AuditStatus.FAILED) {
                return a;
            }
            Thread.sleep(50);
        }
        fail("Timed out waiting for model audit " + auditId + " to reach terminal state");
        return null;
    }

    private void cleanupAuditHistory(String targetName) {
        jdbcTemplate.update("DELETE FROM audit_history WHERE target_name = ?", targetName);
    }
}
