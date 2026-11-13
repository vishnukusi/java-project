package com.sentinelcv.integration;

import com.sentinelcv.config.ApplicationConfig;
import com.sentinelcv.dataset.CocoParser;
import com.sentinelcv.dataset.DatasetParser;
import com.sentinelcv.dataset.YoloParser;
import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DatasetAudit;
import com.sentinelcv.entity.DriftAudit;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.exception.DatasetParsingException;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.service.DatasetAuditService;
import com.sentinelcv.service.DriftDetectionService;
import com.sentinelcv.util.ArchiveUtil;
import com.sentinelcv.util.HashUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class DatasetParserDispatchRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetAuditRepository datasetAuditRepository;

    @Autowired
    private DatasetAuditService datasetAuditService;

    @Autowired
    private DriftDetectionService driftDetectionService;

    @Autowired
    private CocoParser cocoParser;

    @Autowired
    private YoloParser yoloParser;

    @Autowired
    private ApplicationConfig applicationConfig;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdDatasetIds = new ArrayList<>();
    private final List<Long> createdDriftIds = new ArrayList<>();
    private final List<Path> createdStorageDirs = new ArrayList<>();

    @AfterEach
    void cleanupTestArtifacts() {
        for (Long driftId : createdDriftIds) {
            jdbcTemplate.update("DELETE FROM security_findings WHERE source_audit_type = 'DRIFT' AND source_audit_id = ?", driftId);
            jdbcTemplate.update("DELETE FROM audit_history WHERE audit_type = 'DRIFT' AND target_id = ?", driftId);
            jdbcTemplate.update("DELETE FROM drift_audits WHERE id = ?", driftId);
        }
        createdDriftIds.clear();

        for (Long datasetId : createdDatasetIds) {
            List<DatasetAudit> audits = datasetAuditRepository.findByDatasetId(datasetId);
            for (DatasetAudit audit : audits) {
                jdbcTemplate.update("DELETE FROM mandatory_check_statuses WHERE check_id = 'dataset.integrity' AND audit_id = ?", audit.getId());
                jdbcTemplate.update("DELETE FROM security_findings WHERE source_audit_type = 'DATASET' AND source_audit_id = ?", audit.getId());
            }
            jdbcTemplate.update("DELETE FROM audit_history WHERE audit_type = 'DATASET' AND target_id = ?", datasetId);
            datasetRepository.deleteById(datasetId);
        }
        createdDatasetIds.clear();

        for (Path dir : createdStorageDirs) {
            ArchiveUtil.deleteDirectoryRecursively(dir);
        }
        createdStorageDirs.clear();
    }

    @Test
    void testCocoUploadPersistsFormatAndExecutesCocoParser(CapturedOutput output, @TempDir Path tempDir) throws Exception {
        byte[] cocoZip = buildCocoZip(3);

        // Even when YoloParser appears before CocoParser in list injection, COCO format must resolve CocoParser.
        Dataset cocoProbe = new Dataset("Order Probe", DatasetFormat.COCO, tempDir.toString());
        DatasetParser resolved = DatasetParser.resolveAuthoritativeParser(List.of(yoloParser, cocoParser), cocoProbe);
        assertSame(cocoParser, resolved, "COCO dataset must strictly resolve CocoParser even when YoloParser is listed first");

        Long datasetId = uploadOrStageDataset(
                "Regression COCO Dataset",
                "COCO",
                "yolo_named_trap.zip",
                cocoZip,
                tempDir
        );

        Dataset persisted = datasetRepository.findById(datasetId).orElseThrow();
        assertEquals(DatasetFormat.COCO, persisted.getFormat(), "Persisted dataset format must be COCO");

        DatasetAudit audit = datasetAuditRepository.save(new DatasetAudit(datasetId));
        DatasetAudit completed = datasetAuditService.executeAudit(audit.getId());

        assertEquals(AuditStatus.COMPLETED, completed.getStatus(), () -> "Unexpected failure: " + completed.getErrorMessage());
        Dataset refreshed = datasetRepository.findById(datasetId).orElseThrow();
        assertEquals(3, refreshed.getTotalImages(), "COCO image count must be parsed");
        assertEquals(4, refreshed.getTotalAnnotations(), "COCO bounding-box annotations must actually be parsed");
        assertEquals(2, refreshed.getTotalClasses(), "COCO categories must actually be parsed");

        String logs = output.getOut();
        assertTrue(logs.contains("persistedFormat=COCO) using parser: CocoParser"),
                "Audit log must explicitly record CocoParser selection for COCO dataset");
        assertTrue(logs.contains("COCO parse complete: 3 images, 4 annotations, 2 categories"),
                "CocoParser must actually execute and parse COCO annotations");
    }

    @Test
    void testYoloUploadPersistsFormatAndExecutesYoloParser(CapturedOutput output, @TempDir Path tempDir) throws Exception {
        byte[] yoloZip = buildYoloZip(2);

        Dataset yoloProbe = new Dataset("Order Probe", DatasetFormat.YOLO, tempDir.toString());
        DatasetParser resolved = DatasetParser.resolveAuthoritativeParser(List.of(cocoParser, yoloParser), yoloProbe);
        assertSame(yoloParser, resolved, "YOLO dataset must strictly resolve YoloParser");

        Long datasetId = uploadOrStageDataset(
                "Regression YOLO Dataset",
                "YOLO",
                "coco_named_trap.zip",
                yoloZip,
                tempDir
        );

        Dataset persisted = datasetRepository.findById(datasetId).orElseThrow();
        assertEquals(DatasetFormat.YOLO, persisted.getFormat(), "Persisted dataset format must be YOLO");

        DatasetAudit audit = datasetAuditRepository.save(new DatasetAudit(datasetId));
        DatasetAudit completed = datasetAuditService.executeAudit(audit.getId());

        assertEquals(AuditStatus.COMPLETED, completed.getStatus(), () -> "Unexpected failure: " + completed.getErrorMessage());
        Dataset refreshed = datasetRepository.findById(datasetId).orElseThrow();
        assertEquals(2, refreshed.getTotalImages(), "YOLO image count must be parsed");
        assertEquals(3, refreshed.getTotalAnnotations(), "YOLO label annotations must actually be parsed");
        assertEquals(2, refreshed.getTotalClasses(), "YOLO classes must actually be parsed");

        String logs = output.getOut();
        assertTrue(logs.contains("persistedFormat=YOLO) using parser: YoloParser"),
                "Audit log must explicitly record YoloParser selection for YOLO dataset");
        assertTrue(logs.contains("YOLO parse complete: 2 images, 3 annotations, 2 categories"),
                "YoloParser must actually execute and parse YOLO annotations");
    }

    @Test
    void testUnknownOrMissingFormatFailsExplicitly(@TempDir Path tempDir) throws Exception {
        long countBefore = datasetRepository.count();
        byte[] cocoZip = buildCocoZip(2);

        MockMultipartFile unknownArchive = new MockMultipartFile(
                "archive", "dataset.zip", "application/zip", cocoZip);

        mockMvc.perform(multipart("/datasets/upload")
                        .file(unknownArchive)
                        .param("name", "Unsupported Format DS")
                        .param("format", "PASCAL_VOC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/datasets"))
                .andExpect(flash().attribute("error", containsString("Unsupported dataset format 'PASCAL_VOC'")));

        assertEquals(countBefore, datasetRepository.count(), "Unsupported format upload must not register any dataset");

        MockMultipartFile missingFmtArchive = new MockMultipartFile(
                "archive", "dataset.zip", "application/zip", cocoZip);

        mockMvc.perform(multipart("/datasets/upload")
                        .file(missingFmtArchive)
                        .param("name", "Missing Format DS")
                        .param("format", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/datasets"))
                .andExpect(flash().attribute("error", containsString("Dataset format is required")));

        assertEquals(countBefore, datasetRepository.count(), "Blank format upload must not register any dataset");

        // Service-level fail-closed check when persisted dataset format is null, even if directory contains YOLO files
        Path yoloDir = tempDir.resolve("yolo_with_null_format");
        extractZipForTest(buildYoloZip(2), yoloDir);

        DatasetAudit audit = new DatasetAudit(999991L);
        audit.setId(999991L);
        Dataset nullFormatDataset = new Dataset("Null Format DS", null, yoloDir.toAbsolutePath().toString());
        nullFormatDataset.setId(999991L);

        DatasetAuditRepository mockAuditRepo = Mockito.mock(DatasetAuditRepository.class);
        DatasetRepository mockDatasetRepo = Mockito.mock(DatasetRepository.class);
        Mockito.when(mockAuditRepo.findById(999991L)).thenReturn(Optional.of(audit));
        Mockito.when(mockAuditRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(mockDatasetRepo.findById(999991L)).thenReturn(Optional.of(nullFormatDataset));

        DatasetAuditService isolatedService = new DatasetAuditService(
                List.of(yoloParser, cocoParser),
                null, null, null, null,
                mockDatasetRepo,
                mockAuditRepo,
                null, null, null, null
        );

        DatasetAudit failed = isolatedService.executeAudit(999991L);
        assertEquals(AuditStatus.FAILED, failed.getStatus());
        assertNotNull(failed.getErrorMessage());
        assertTrue(failed.getErrorMessage().contains("Dataset format is missing"));

        // Resolver check when no parser is registered for the dataset's persisted format
        Dataset cocoDataset = new Dataset("No COCO Parser DS", DatasetFormat.COCO, yoloDir.toAbsolutePath().toString());
        assertThrows(DatasetParsingException.class,
                () -> DatasetParser.resolveAuthoritativeParser(List.of(yoloParser), cocoDataset),
                "Missing COCO parser must throw explicitly and never fall back to YoloParser");
    }

    @Test
    void testCocoFormatDoesNotSilentlyFallBackToYolo(CapturedOutput output, @TempDir Path tempDir) throws Exception {
        // Directory has YOLO images/ and labels/ only (no COCO JSON), persisted authoritatively as COCO
        Path yoloLayoutDir = tempDir.resolve("mislabeled_as_coco");
        extractZipForTest(buildYoloZip(2), yoloLayoutDir);

        Dataset dataset = new Dataset("Mislabeled As COCO", DatasetFormat.COCO, yoloLayoutDir.toAbsolutePath().toString());
        dataset.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(yoloLayoutDir.toFile()));
        dataset = datasetRepository.save(dataset);
        createdDatasetIds.add(dataset.getId());

        DatasetAudit audit = datasetAuditRepository.save(new DatasetAudit(dataset.getId()));
        DatasetAudit result = datasetAuditService.executeAudit(audit.getId());

        assertEquals(AuditStatus.FAILED, result.getStatus(),
                "COCO dataset missing COCO annotations must fail rather than falling back to YoloParser");
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("No valid COCO JSON annotation file found"));
        assertTrue(output.getOut().contains("persistedFormat=COCO) using parser: CocoParser"));
    }

    @Test
    void testDriftDetectionUsesAuthoritativeParsersForCocoAndYolo(CapturedOutput output, @TempDir Path tempDir) throws Exception {
        Path cocoDir = tempDir.resolve("drift_coco_5");
        Path yoloDir = tempDir.resolve("drift_yolo_5");
        extractZipForTest(buildCocoZip(5), cocoDir);
        extractZipForTest(buildYoloZip(5), yoloDir);

        Dataset cocoDs = new Dataset("Drift Ref COCO", DatasetFormat.COCO, cocoDir.toAbsolutePath().toString());
        cocoDs.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(cocoDir.toFile()));
        cocoDs = datasetRepository.save(cocoDs);
        createdDatasetIds.add(cocoDs.getId());

        Dataset yoloDs = new Dataset("Drift Eval YOLO", DatasetFormat.YOLO, yoloDir.toAbsolutePath().toString());
        yoloDs.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(yoloDir.toFile()));
        yoloDs = datasetRepository.save(yoloDs);
        createdDatasetIds.add(yoloDs.getId());

        DriftAudit driftAudit = driftDetectionService.executeDriftAudit(cocoDs.getId(), yoloDs.getId());
        createdDriftIds.add(driftAudit.getId());

        assertEquals("COMPLETED", driftAudit.getStatus(), () -> "Drift failed: " + driftAudit.getSummary());
        String logs = output.getOut();
        assertTrue(logs.contains("persistedFormat=COCO) using parser: CocoParser"));
        assertTrue(logs.contains("persistedFormat=YOLO) using parser: YoloParser"));
    }

    private Long uploadOrStageDataset(
            String name,
            String format,
            String zipFilename,
            byte[] zipBytes,
            Path tempDir
    ) throws Exception {
        Path datasetsRoot = Path.of(applicationConfig.getDatasetsDir()).toAbsolutePath().normalize();
        Files.createDirectories(datasetsRoot);

        if (ArchiveUtil.isSecureDirectoryStreamSupported(datasetsRoot)) {
            MockMultipartFile archive = new MockMultipartFile(
                    "archive", zipFilename, "application/zip", zipBytes);
            MvcResult res = mockMvc.perform(multipart("/datasets/upload")
                            .file(archive)
                            .param("name", name)
                            .param("format", format))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();

            String location = res.getResponse().getRedirectedUrl();
            assertNotNull(location, "Upload must redirect");
            assertTrue(location.matches("/datasets/\\d+"), "Expected /datasets/{id} redirect, got: " + location);

            Long datasetId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
            Dataset persisted = datasetRepository.findById(datasetId).orElseThrow();
            createdDatasetIds.add(datasetId);
            createdStorageDirs.add(Path.of(persisted.getPath()));
            return datasetId;
        }

        // On platforms without SecureDirectoryStream (e.g. macOS local dev), verify upload fails closed first,
        // then stage extracted contents directly to exercise DB persistence and authoritative parser dispatch.
        MockMultipartFile archive = new MockMultipartFile(
                "archive", zipFilename, "application/zip", zipBytes);
        mockMvc.perform(multipart("/datasets/upload")
                        .file(archive)
                        .param("name", name + " FailClosed Check")
                        .param("format", format))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/datasets"));

        Path destDir = tempDir.resolve(name.replaceAll("[^a-zA-Z0-9_-]", "_"));
        extractZipForTest(zipBytes, destDir);
        Dataset ds = new Dataset(name, DatasetFormat.valueOf(format), destDir.toAbsolutePath().toString());
        ds.setFileSizeBytes(zipBytes.length);
        ds.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(destDir.toFile()));
        ds = datasetRepository.save(ds);
        createdDatasetIds.add(ds.getId());
        return ds.getId();
    }

    private static byte[] buildCocoZip(int imageCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            StringBuilder images = new StringBuilder();
            StringBuilder annotations = new StringBuilder();
            int annId = 1;
            for (int i = 1; i <= imageCount; i++) {
                String fname = "cam_" + i + ".jpg";
                addZipEntry(zos, "images/" + fname, renderJpg(240, 180, new Color(30 * i, 60, 120), new Color(220, 40 * i, 30)));
                if (i > 1) {
                    images.append(',');
                }
                images.append(String.format(
                        "{\"id\":%d,\"file_name\":\"%s\",\"width\":240,\"height\":180}", i, fname));

                int boxes = (i == 1) ? 2 : 1; // total = imageCount + 1 annotations (e.g., 4 for 3 images)
                for (int b = 0; b < boxes; b++) {
                    if (annId > 1) {
                        annotations.append(',');
                    }
                    int catId = ((i + b) % 2 == 0) ? 2 : 1;
                    annotations.append(String.format(
                            "{\"id\":%d,\"image_id\":%d,\"category_id\":%d,\"bbox\":[%d.0,%d.0,50.0,40.0],\"area\":2000.0,\"iscrowd\":0}",
                            annId++, i, catId, 20 + b * 40, 30 + i * 5));
                }
            }
            String json = "{"
                    + "\"info\":{\"description\":\"Regression COCO Fixture\",\"contributor\":\"SentinelCV QA\",\"date_created\":\"2026-11-12\"},"
                    + "\"categories\":[{\"id\":1,\"name\":\"vehicle\"},{\"id\":2,\"name\":\"person\"}],"
                    + "\"images\":[" + images + "],"
                    + "\"annotations\":[" + annotations + "]"
                    + "}";
            addZipEntry(zos, "annotations.json", json.getBytes(StandardCharsets.UTF_8));
        }
        return baos.toByteArray();
    }

    private static byte[] buildYoloZip(int imageCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addZipEntry(zos, "data.yaml", "names:\n  0: vehicle\n  1: person\nnc: 2\n".getBytes(StandardCharsets.UTF_8));
            for (int i = 1; i <= imageCount; i++) {
                String stem = "frame_" + i;
                addZipEntry(zos, "images/" + stem + ".jpg", renderJpg(240, 180, new Color(160, 25 * i, 40), new Color(30, 180, 35 * i)));
                StringBuilder labels = new StringBuilder();
                labels.append(String.format("%d 0.45 0.50 0.25 0.30%n", i % 2));
                if (i == 1) {
                    labels.append("1 0.70 0.40 0.20 0.25\n"); // total = imageCount + 1 annotations (e.g., 3 for 2 images)
                }
                addZipEntry(zos, "labels/" + stem + ".txt", labels.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        return baos.toByteArray();
    }

    private static void addZipEntry(ZipOutputStream zos, String name, byte[] bytes) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(bytes);
        zos.closeEntry();
    }

    private static byte[] renderJpg(int w, int h, Color bg, Color fg) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(bg);
        g.fillRect(0, 0, w, h);
        g.setColor(fg);
        g.fillRect(20, 20, 60, 50);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

    private static void extractZipForTest(byte[] zipBytes, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (var zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = destDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(destDir)) {
                    throw new IOException("Zip slip in test fixture");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.write(out, zis.readAllBytes());
                }
            }
        }
    }
}

