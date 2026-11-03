package com.sentinelcv.util;

import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DatasetAudit;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.exception.SentinelCvException;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.MandatoryCheckStatusRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.service.DatasetAuditService;
import com.sentinelcv.service.RiskAssessmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DatasetSecurityTest {

    @Autowired(required = false)
    private DatasetRepository datasetRepository;

    @Autowired(required = false)
    private DatasetAuditRepository datasetAuditRepository;

    @Autowired(required = false)
    private DatasetAuditService datasetAuditService;

    @Autowired(required = false)
    private RiskAssessmentService riskAssessmentService;

    @Autowired(required = false)
    private MandatoryCheckStatusRepository checkStatusRepository;

    @Autowired(required = false)
    private SecurityFindingRepository findingRepository;

    @BeforeEach
    @AfterEach
    void resetHook() {
        HashUtil.HASHING_INTERCEPTOR = null;
        if (findingRepository != null) {
            findingRepository.deleteAll();
        }
    }

    // =========================================================================
    // MANDATORY DATASET ADVERSARIAL TESTS (PART 3 - B5)
    // =========================================================================

    /**
     * 1. testEveryRegularFileContributesToDatasetHash
     * Every regular file in the directory must contribute to the manifest hash.
     */
    @Test
    void testEveryRegularFileContributesToDatasetHash(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("image1.jpg"), "IMG_DATA_1");
        Files.writeString(tempDir.resolve("labels.txt"), "LABELS_DATA");
        Files.writeString(tempDir.resolve(".hidden_annotation"), "HIDDEN_DATA");
        Files.writeString(tempDir.resolve("weights.bin"), "BINARY_DATA");

        String hash1 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());
        assertNotNull(hash1);

        // Modify binary file
        Files.writeString(tempDir.resolve("weights.bin"), "BINARY_DATA_TAMPERED");
        String hash2 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());
        assertNotEquals(hash1, hash2, "Modifying weights.bin must change dataset directory hash");

        // Modify hidden annotation file
        Files.writeString(tempDir.resolve(".hidden_annotation"), "HIDDEN_DATA_TAMPERED");
        String hash3 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());
        assertNotEquals(hash2, hash3, "Modifying hidden file must change dataset directory hash");
    }

    /**
     * 2. testTmpNamedDatasetFileModificationChangesHash
     * Files named *.tmp must NOT be excluded; modifying them must alter the hash.
     */
    @Test
    void testTmpNamedDatasetFileModificationChangesHash(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("frame1.jpg"), "FRAME_DATA");
        Files.writeString(tempDir.resolve("cache.tmp"), "CACHE_DATA_V1");

        String hash1 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());

        Files.writeString(tempDir.resolve("cache.tmp"), "CACHE_DATA_V2_POISONED");
        String hash2 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());

        assertNotEquals(hash1, hash2,
                "Files matching *.tmp must contribute to hash; modifications must not be masked");
    }

    /**
     * 3. testMacMetadataNamedFileModificationChangesHash
     * OS metadata names (.DS_Store, Thumbs.db, ._*) must NOT be excluded.
     */
    @Test
    void testMacMetadataNamedFileModificationChangesHash(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("image.png"), "IMAGE_PIXELS");
        Files.writeString(tempDir.resolve(".DS_Store"), "MAC_FINDER_METADATA_1");
        Files.writeString(tempDir.resolve("Thumbs.db"), "WIN_THUMBNAIL_CACHE_1");
        Files.writeString(tempDir.resolve("._image.png"), "APPLE_DOUBLE_HEADER_1");

        String hash1 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());

        // Modify .DS_Store
        Files.writeString(tempDir.resolve(".DS_Store"), "MAC_FINDER_METADATA_2_POISONED");
        String hash2 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());
        assertNotEquals(hash1, hash2, "Modifying .DS_Store must alter dataset content hash");

        // Modify Thumbs.db
        Files.writeString(tempDir.resolve("Thumbs.db"), "WIN_THUMBNAIL_CACHE_2_POISONED");
        String hash3 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());
        assertNotEquals(hash2, hash3, "Modifying Thumbs.db must alter dataset content hash");

        // Modify ._* AppleDouble file
        Files.writeString(tempDir.resolve("._image.png"), "APPLE_DOUBLE_HEADER_2_POISONED");
        String hash4 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());
        assertNotEquals(hash3, hash4, "Modifying ._* file must alter dataset content hash");
    }

    /**
     * 4. testNewFileChangesDatasetHash
     * Adding an unrecorded file to the dataset directory must alter the hash.
     */
    @Test
    void testNewFileChangesDatasetHash(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("train_1.jpg"), "TRAIN_DATA");
        String hash1 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());

        Files.writeString(tempDir.resolve("train_2_backdoor.jpg"), "BACKDOOR_IMAGE");
        String hash2 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());

        assertNotEquals(hash1, hash2, "Adding a new file must alter the dataset directory hash");
    }

    /**
     * 5. testDeletedFileChangesDatasetHash
     * Deleting any file must alter the dataset hash.
     */
    @Test
    void testDeletedFileChangesDatasetHash(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("sample_a.jpg"), "SAMPLE_A");
        Files.writeString(tempDir.resolve("sample_b.jpg"), "SAMPLE_B");

        String hash1 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());

        Files.delete(tempDir.resolve("sample_b.jpg"));
        String hash2 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());

        assertNotEquals(hash1, hash2, "Deleting a file must alter the dataset directory hash");
    }

    /**
     * 6. testRenamedFileChangesDatasetHash
     * Renaming a file must alter the dataset hash even if content remains identical.
     */
    @Test
    void testRenamedFileChangesDatasetHash(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("original_name.jpg"), "IDENTICAL_IMAGE_BYTES");
        String hash1 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());

        Files.move(tempDir.resolve("original_name.jpg"), tempDir.resolve("renamed_name.jpg"));
        String hash2 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());

        assertNotEquals(hash1, hash2,
                "Renaming a file must alter the dataset hash due to canonical path encoding");
    }

    /**
     * 7. testIdenticalContentsProduceDeterministicHash
     * Identical directory contents across two different directory locations must produce identical hashes.
     */
    @Test
    void testIdenticalContentsProduceDeterministicHash(@TempDir Path tempDir) throws Exception {
        Path dirA = tempDir.resolve("dirA");
        Path dirB = tempDir.resolve("dirB");
        Files.createDirectories(dirA.resolve("subdir"));
        Files.createDirectories(dirB.resolve("subdir"));

        Files.writeString(dirA.resolve("file1.txt"), "COMMON_CONTENT_1");
        Files.writeString(dirB.resolve("file1.txt"), "COMMON_CONTENT_1");

        Files.writeString(dirA.resolve("subdir/file2.txt"), "COMMON_CONTENT_2");
        Files.writeString(dirB.resolve("subdir/file2.txt"), "COMMON_CONTENT_2");

        String hashA = HashUtil.calculateDatasetDirectoryHash(dirA.toFile());
        String hashB = HashUtil.calculateDatasetDirectoryHash(dirB.toFile());

        assertEquals(hashA, hashB,
                "Identical dataset contents must produce exactly identical cryptographic hashes");
    }

    /**
     * 8. testEnumerationOrderDoesNotAffectHash
     * Filesystem traversal order must not affect the computed hash; sorting must be canonical.
     */
    @Test
    void testEnumerationOrderDoesNotAffectHash(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("z_last.jpg"), "Z_CONTENT");
        Files.writeString(tempDir.resolve("a_first.jpg"), "A_CONTENT");
        Files.writeString(tempDir.resolve("m_middle.jpg"), "M_CONTENT");

        String hashRun1 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());
        String hashRun2 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());
        String hashRun3 = HashUtil.calculateDatasetDirectoryHash(tempDir.toFile());

        assertEquals(hashRun1, hashRun2);
        assertEquals(hashRun2, hashRun3);
    }

    /**
     * 9. testSymlinkInsertionRejected
     * Inserting a symbolic link into the dataset tree must immediately fail closed.
     */
    @Test
    void testSymlinkInsertionRejected(@TempDir Path tempDir) throws Exception {
        Path outsideFile = tempDir.resolve("outside_sensitive.txt");
        Files.writeString(outsideFile, "CONFIDENTIAL_HOST_DATA");

        Path datasetDir = tempDir.resolve("dataset_with_symlink");
        Files.createDirectories(datasetDir);
        Files.writeString(datasetDir.resolve("normal_image.jpg"), "IMAGE_DATA");

        Path symlinkTarget = datasetDir.resolve("symlink_pointer.jpg");
        try {
            Files.createSymbolicLink(symlinkTarget, outsideFile);
        } catch (UnsupportedOperationException | IOException e) {
            return; // Skip on platforms without symlink support
        }

        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                HashUtil.calculateDatasetDirectoryHash(datasetDir.toFile()));
        assertEquals("SECURITY_VIOLATION", ex.getErrorCode(),
                "Symlink insertion into dataset directory must fail closed with SECURITY_VIOLATION");
    }

    /**
     * 10. testSpecialFileRejected
     * Special non-regular files (such as named pipes / FIFOs) must be rejected immediately.
     */
    @Test
    void testSpecialFileRejected(@TempDir Path tempDir) throws Exception {
        Path datasetDir = tempDir.resolve("special_file_dataset");
        Files.createDirectories(datasetDir);
        Files.writeString(datasetDir.resolve("clean.jpg"), "PIXELS");

        Path fifoPath = datasetDir.resolve("fifo_pipe");
        try {
            ProcessBuilder pb = new ProcessBuilder("mkfifo", fifoPath.toAbsolutePath().toString());
            Process p = pb.start();
            if (p.waitFor() != 0 || !Files.exists(fifoPath)) {
                return; // Platform doesn't support mkfifo
            }
        } catch (Exception e) {
            return; // Skip if mkfifo command unavailable
        }

        try {
            SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                    HashUtil.calculateDatasetDirectoryHash(datasetDir.toFile()));
            assertEquals("SECURITY_VIOLATION", ex.getErrorCode(),
                    "Special non-regular files must be rejected immediately with SECURITY_VIOLATION");
        } finally {
            Files.deleteIfExists(fifoPath);
        }
    }

    /**
     * 11. testConcurrentFileModificationDuringHashingRejected
     * A file modified while hashing is underway must be detected and rejected (CONCURRENT_MUTATION_DETECTED).
     */
    @Test
    void testConcurrentFileModificationDuringHashingRejected(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("file_1.jpg"), "ORIGINAL_1");
        Files.writeString(tempDir.resolve("file_2.jpg"), "ORIGINAL_2");

        // Test hook simulates an attacker tampering with file_1 after enumeration
        HashUtil.HASHING_INTERCEPTOR = (relPath) -> {
            if ("file_1.jpg".equals(relPath)) {
                try {
                    Files.writeString(tempDir.resolve("file_1.jpg"), "CONCURRENTLY_MUTATED_PAYLOAD");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                HashUtil.calculateDatasetDirectoryHash(tempDir.toFile()));
        assertEquals("CONCURRENT_MUTATION_DETECTED", ex.getErrorCode(),
                "Concurrent file mutation during hashing must be detected and rejected");
    }

    /**
     * 12. testDirectoryMutationDuringManifestGenerationRejected
     * A new file injected into the directory during manifest generation must trigger CONCURRENT_MUTATION_DETECTED.
     */
    @Test
    void testDirectoryMutationDuringManifestGenerationRejected(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("first.jpg"), "FIRST_DATA");

        // Test hook adds an unrecorded file during the hashing window
        HashUtil.HASHING_INTERCEPTOR = (relPath) -> {
            try {
                Path injected = tempDir.resolve("injected_mid_hashing.jpg");
                if (!Files.exists(injected)) {
                    Files.writeString(injected, "INJECTED_ATTACK_FILE");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                HashUtil.calculateDatasetDirectoryHash(tempDir.toFile()));
        assertEquals("CONCURRENT_MUTATION_DETECTED", ex.getErrorCode(),
                "Directory structure mutation during manifest generation must trigger CONCURRENT_MUTATION_DETECTED");
    }

    /**
     * 13. testHashingFailureNeverProducesPassedStatus
     * A dataset hashing failure during audit must never produce PASSED status or ACCEPT decision.
     */
    @Test
    void testHashingFailureNeverProducesPassedStatus(@TempDir Path tempDir) throws Exception {
        if (datasetRepository == null || datasetAuditService == null || riskAssessmentService == null) {
            return;
        }

        // Create dataset directory with a symlink that causes HashUtil to fail closed
        Path dsDir = tempDir.resolve("failing_hash_ds");
        Files.createDirectories(dsDir);
        Files.writeString(dsDir.resolve("img.jpg"), "VALID");

        Path outsideSecret = tempDir.resolve("outside.txt");
        Files.writeString(outsideSecret, "SECRET");
        try {
            Files.createSymbolicLink(dsDir.resolve("bad_link.jpg"), outsideSecret);
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }

        Dataset ds = new Dataset("Tampered Hash DS", DatasetFormat.COCO, dsDir.toAbsolutePath().toString());
        ds.setSha256Hash("EXPECTED_HASH");
        ds = datasetRepository.save(ds);

        DatasetAudit audit = new DatasetAudit(ds.getId());
        audit = datasetAuditRepository.save(audit);
        audit = datasetAuditService.executeAudit(audit.getId());
        assertNotEquals(AssessmentDecision.ACCEPT, audit.getAssessmentDecision(),
                "Audit on unhashable/tampered dataset must NEVER result in ACCEPT");

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("dataset.integrity");
        assertNotEquals(CheckExecutionStatus.PASSED, status,
                "Hashing failure must never produce PASSED mandatory check status");
    }

    /**
     * 14. testDatasetModificationOnDiskProducesReview
     * Modifying a dataset on disk without an audit invalidates freshness and mandates REVIEW.
     */
    @Test
    void testDatasetModificationOnDiskProducesReview(@TempDir Path tempDir) throws Exception {
        if (datasetRepository == null || riskAssessmentService == null || datasetAuditRepository == null) {
            return;
        }

        Path dsDir = tempDir.resolve("freshness_ds");
        Files.createDirectories(dsDir);
        Files.writeString(dsDir.resolve("img.jpg"), "ORIGINAL_IMG");

        String initialHash = HashUtil.calculateDatasetDirectoryHash(dsDir.toFile());

        Dataset ds = new Dataset("Freshness Test DS", DatasetFormat.COCO, dsDir.toAbsolutePath().toString());
        ds.setSha256Hash(initialHash);
        ds = datasetRepository.save(ds);

        DatasetAudit audit = new DatasetAudit(ds.getId());
        audit.setStatus(AuditStatus.COMPLETED);
        audit = datasetAuditRepository.save(audit);

        riskAssessmentService.recordCheckStatus("dataset.integrity", audit.getId(),
                String.valueOf(ds.getId()), initialHash, CheckExecutionStatus.PASSED, "Passed initially");

        // Before modification: PASSED
        assertEquals(CheckExecutionStatus.PASSED, riskAssessmentService.getCheckStatus("dataset.integrity"));

        // Physically modify a file on disk
        Files.writeString(dsDir.resolve("img.jpg"), "TAMPERED_IMG_PAYLOAD");

        // Freshness check must detect mismatch and invalidate status
        CheckExecutionStatus staleStatus = riskAssessmentService.getCheckStatus("dataset.integrity");
        assertEquals(CheckExecutionStatus.NOT_EXECUTED, staleStatus,
                "On-disk dataset modification must invalidate check status to NOT_EXECUTED");

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertNotEquals(AssessmentDecision.ACCEPT, assessment.overallDecision,
                "Tampered dataset on disk must never produce ACCEPT");
        assertTrue(assessment.overallDecision == AssessmentDecision.REVIEW ||
                   assessment.overallDecision == AssessmentDecision.QUARANTINE,
                "Tampered dataset on disk must mandate REVIEW or QUARANTINE");
    }
}
