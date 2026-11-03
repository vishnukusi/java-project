package com.sentinelcv.util;

import com.sentinelcv.exception.SentinelCvException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ArchiveSecurityTest {

    @BeforeEach
    @AfterEach
    void cleanupHooks() {
        ArchiveUtil.COMMIT_INTERCEPTOR = null;
    }

    // =========================================================================
    // MANDATORY ARCHIVE ADVERSARIAL TESTS (PART 2 - A5)
    // =========================================================================

    /**
     * 1. testPreExistingIntermediateSymlinkRejected
     * Pre-existing intermediate directory component pointing outside target confinement must be rejected.
     */
    @Test
    void testPreExistingIntermediateSymlinkRejected(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("intermediate_attack.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry e = new ZipEntry("intermediate/data/payload.txt");
            zos.putNextEntry(e);
            zos.write("ATTACK_PAYLOAD".getBytes());
            zos.closeEntry();
        }

        Path outsideDir = tempDir.resolve("outside_sensitive_dir");
        Files.createDirectories(outsideDir);

        Path destDir = tempDir.resolve("dest_intermediate_symlink");
        Files.createDirectories(destDir);

        Path symlinkComponent = destDir.resolve("intermediate");
        try {
            Files.createSymbolicLink(symlinkComponent, outsideDir);
        } catch (UnsupportedOperationException | IOException e) {
            return; // Skip on platforms without symlink support
        }

        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(zipFile, destDir));
        assertEquals("SECURITY_VIOLATION", ex.getErrorCode());
        assertFalse(Files.exists(outsideDir.resolve("data/payload.txt")),
                "Attack payload must never be written into directory outside target confinement");
    }

    /**
     * 2. testIntermediateDirectorySymlinkSwapDuringCommitRejected
     * Deterministic TOCTOU race: An attacker swaps an intermediate directory with a symlink
     * during the commit window using synchronization hooks.
     */
    @Test
    void testIntermediateDirectorySymlinkSwapDuringCommitRejected(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("toctou_race.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry e = new ZipEntry("subdir/payload.txt");
            zos.putNextEntry(e);
            zos.write("ATTACK_PAYLOAD_TOCTOU".getBytes());
            zos.closeEntry();
        }

        Path outsideDir = tempDir.resolve("outside_toctou_target");
        Files.createDirectories(outsideDir);

        Path destDir = tempDir.resolve("dest_toctou_race");
        Files.createDirectories(destDir);

        if (!ArchiveUtil.isSecureDirectoryStreamSupported(destDir)) {
            SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                    ArchiveUtil.extractZipSafely(zipFile, destDir));
            assertEquals("UNSUPPORTED_FILESYSTEM", ex.getErrorCode());
            assertFalse(Files.exists(outsideDir.resolve("payload.txt")));
            return;
        }

        // Hook triggered right before the file is opened/created inside parentDir
        ArchiveUtil.COMMIT_INTERCEPTOR = (stage, parentDir) -> {
            if ("before_file_creation".equals(stage) && parentDir.endsWith("subdir")) {
                try {
                    // Attacker swaps 'subdir' with a symlink to outsideDir
                    ArchiveUtil.deleteDirectoryRecursively(parentDir);
                    Files.createSymbolicLink(parentDir, outsideDir);
                } catch (Exception e) {
                    throw new RuntimeException("Attack harness failed to swap directory", e);
                }
            }
        };

        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(zipFile, destDir));

        assertEquals("SECURITY_VIOLATION", ex.getErrorCode(),
                "TOCTOU intermediate directory symlink swap must be detected and rejected");
        assertFalse(Files.exists(outsideDir.resolve("payload.txt")),
                "Attack payload must NEVER escape into outside target directory during race");
    }

    /**
     * 3. testDestinationRootReplacementRejected
     * Destination directory root replaced with a symbolic link pointing to a sensitive location.
     */
    @Test
    void testDestinationRootReplacementRejected(@TempDir Path tempDir) throws Exception {
        File validZip = tempDir.resolve("sample.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(validZip))) {
            ZipEntry entry = new ZipEntry("content.txt");
            zos.putNextEntry(entry);
            zos.write("hello".getBytes());
            zos.closeEntry();
        }

        Path outsideSensitive = tempDir.resolve("sensitive_root_target");
        Files.createDirectories(outsideSensitive);

        Path symlinkDest = tempDir.resolve("symlink_dest_root");
        try {
            Files.createSymbolicLink(symlinkDest, outsideSensitive);
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }

        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(validZip, symlinkDest));
        assertEquals("SECURITY_VIOLATION", ex.getErrorCode());
        assertFalse(Files.exists(outsideSensitive.resolve("content.txt")),
                "Extraction must not follow symlinked destination root");
    }

    /**
     * 4. testConcurrentSameDestinationExtractionRejected
     * Concurrent extractions targeting the exact same destination path must be mutually exclusive.
     */
    @Test
    void testConcurrentSameDestinationExtractionRejected(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("concurrent.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry entry = new ZipEntry("concurrent_data.txt");
            zos.putNextEntry(entry);
            zos.write("Concurrent Payload".getBytes());
            zos.closeEntry();
        }

        Path destDir = tempDir.resolve("concurrent_dest");
        Files.createDirectories(destDir);

        if (!ArchiveUtil.isSecureDirectoryStreamSupported(destDir)) {
            SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                    ArchiveUtil.extractZipSafely(zipFile, destDir));
            assertEquals("UNSUPPORTED_FILESYSTEM", ex.getErrorCode());
            return;
        }

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    ArchiveUtil.extractZipSafely(zipFile, destDir);
                    return true;
                } catch (SentinelCvException sce) {
                    if ("CONCURRENT_EXTRACTION_CONFLICT".equals(sce.getErrorCode()) ||
                        "DESTINATION_COLLISION".equals(sce.getErrorCode())) {
                        return false;
                    }
                    throw sce;
                }
            }));
        }

        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        int successCount = 0;
        int conflictCount = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) {
                successCount++;
            } else {
                conflictCount++;
            }
        }

        assertEquals(1, successCount, "Exactly one thread must succeed in extracting to the destination");
        assertEquals(threadCount - 1, conflictCount,
                "All concurrent conflicting extractions must fail closed");
    }

    /**
     * 5. testSeparateJvmExtractionLockConflict
     * MUST launch a genuinely separate operating-system process/JVM that acquires the file lock.
     * Extraction in this JVM must fail closed with CONCURRENT_EXTRACTION_CONFLICT.
     */
    @Test
    void testSeparateJvmExtractionLockConflict(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("sample_lock.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry e = new ZipEntry("data.txt");
            zos.putNextEntry(e);
            zos.write("CONTENT".getBytes());
            zos.closeEntry();
        }

        Path destDir = tempDir.resolve("dest_separate_jvm_lock");
        Files.createDirectories(destDir);

        String javaHome = System.getProperty("java.home");
        String javaBin = Paths.get(javaHome, "bin", "java").toString();
        String classpath = Paths.get("target", "classes").toAbsolutePath() + File.pathSeparator +
                Paths.get("target", "test-classes").toAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-cp",
                classpath,
                "com.sentinelcv.util.ExternalProcessLockRunner",
                destDir.toAbsolutePath().normalize().toString()
        );
        pb.redirectErrorStream(true);
        Process externalProcess = pb.start();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(externalProcess.getInputStream()));
            String line = reader.readLine();
            assertEquals("LOCKED", line, "Separate OS process must acquire file lock and signal LOCKED");

            SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                    ArchiveUtil.extractZipSafely(zipFile, destDir));
            assertEquals("CONCURRENT_EXTRACTION_CONFLICT", ex.getErrorCode(),
                    "Extraction must fail closed with CONCURRENT_EXTRACTION_CONFLICT when separate JVM process holds lock");

            // Signal external process to release lock and exit
            OutputStream out = externalProcess.getOutputStream();
            out.write("\n".getBytes());
            out.flush();

            boolean finished = externalProcess.waitFor(5, TimeUnit.SECONDS);
            assertTrue(finished, "External process should terminate cleanly after release signal");
            assertEquals(0, externalProcess.exitValue());
        } finally {
            if (externalProcess.isAlive()) {
                externalProcess.destroyForcibly();
            }
        }
    }

    /**
     * 6. testNoExternalFileCreatedDuringAttack
     * Malicious Zip-slip attack archive must not create any file outside the destination confinement.
     */
    @Test
    void testNoExternalFileCreatedDuringAttack(@TempDir Path tempDir) throws Exception {
        File maliciousZip = tempDir.resolve("zipslip_attack.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(maliciousZip))) {
            ZipEntry slipEntry = new ZipEntry("../../escaped_secret_file.txt");
            zos.putNextEntry(slipEntry);
            zos.write("ATTACK_PAYLOAD".getBytes());
            zos.closeEntry();
        }

        Path destDir = tempDir.resolve("extract_attack_dest");

        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(maliciousZip, destDir));
        assertEquals("SECURITY_VIOLATION", ex.getErrorCode());
        assertFalse(Files.exists(tempDir.resolve("escaped_secret_file.txt")),
                "Path traversal file must never be created on disk");
        assertFalse(Files.exists(destDir), "Target directory must be cleaned up on failure");
    }

    /**
     * 7. testExistingFilesNeverOverwritten
     * Pre-existing files in destination must never be overwritten by extracted archive entries.
     */
    @Test
    void testExistingFilesNeverOverwritten(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("overwrite_attempt.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry e = new ZipEntry("protected_user_file.txt");
            zos.putNextEntry(e);
            zos.write("MALICIOUS_OVERWRITE_PAYLOAD".getBytes());
            zos.closeEntry();
        }

        Path destDir = tempDir.resolve("dest_with_existing");
        Files.createDirectories(destDir);
        Path preExisting = destDir.resolve("protected_user_file.txt");
        Files.writeString(preExisting, "GENUINE_ORIGINAL_USER_DATA");

        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(zipFile, destDir));
        assertEquals("DESTINATION_COLLISION", ex.getErrorCode());
        assertEquals("GENUINE_ORIGINAL_USER_DATA", Files.readString(preExisting),
                "Pre-existing destination file must never be modified or overwritten");
    }

    /**
     * 8. testRollbackPreservesPreExistingData
     * Rollback of a failed extraction must preserve pre-existing files in the destination.
     */
    @Test
    void testRollbackPreservesPreExistingData(@TempDir Path tempDir) throws Exception {
        Path destDir = tempDir.resolve("dest_rollback_preserve");
        Files.createDirectories(destDir);
        Path preExistingFile = destDir.resolve("critical_data.csv");
        Files.writeString(preExistingFile, "ORIGINAL_USER_DATA");

        File zipFile = tempDir.resolve("collision_and_new.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry e1 = new ZipEntry("new_file.txt");
            zos.putNextEntry(e1);
            zos.write("new content".getBytes());
            zos.closeEntry();

            ZipEntry e2 = new ZipEntry("critical_data.csv");
            zos.putNextEntry(e2);
            zos.write("overwrite content".getBytes());
            zos.closeEntry();
        }

        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(zipFile, destDir));
        assertEquals("DESTINATION_COLLISION", ex.getErrorCode());

        assertTrue(Files.exists(preExistingFile), "Pre-existing file must survive rollback");
        assertEquals("ORIGINAL_USER_DATA", Files.readString(preExistingFile));
        assertFalse(Files.exists(destDir.resolve("new_file.txt")),
                "New file from failing archive extraction must be cleaned up");
    }

    /**
     * 9. testRollbackDoesNotDeleteUnrelatedFiles
     * Rollback must never delete files created in parent or sibling directories.
     */
    @Test
    void testRollbackDoesNotDeleteUnrelatedFiles(@TempDir Path tempDir) throws Exception {
        Path parentDir = tempDir.resolve("parent_work_dir");
        Files.createDirectories(parentDir);
        Path siblingDir = parentDir.resolve("sibling_dir");
        Files.createDirectories(siblingDir);
        Path siblingFile = siblingDir.resolve("unrelated_project_file.txt");
        Files.writeString(siblingFile, "KEEP_ME_INTACT");
        Path parentFile = parentDir.resolve("parent_notes.txt");
        Files.writeString(parentFile, "PARENT_DATA");

        Path extractDest = parentDir.resolve("failing_extract_dest");

        File badZip = tempDir.resolve("invalid_magic.zip").toFile();
        try (FileWriter fw = new FileWriter(badZip)) {
            fw.write("NOT_A_VALID_ZIP");
        }

        assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(badZip, extractDest));

        assertTrue(Files.exists(siblingFile), "Sibling directory files must remain completely untouched");
        assertEquals("KEEP_ME_INTACT", Files.readString(siblingFile));
        assertTrue(Files.exists(parentFile), "Parent directory files must remain completely untouched");
        assertEquals("PARENT_DATA", Files.readString(parentFile));
    }

    /**
     * 10. testUnsupportedSecureFilesystemFailsBeforeCommit
     * When running on an unsupported filesystem provider (e.g. macOS UnixDirectoryStream without SDS),
     * extraction without explicit fallback must fail closed before commit with UNSUPPORTED_FILESYSTEM.
     */
    @Test
    void testUnsupportedSecureFilesystemFailsBeforeCommit(@TempDir Path tempDir) throws Exception {
        File validZip = tempDir.resolve("valid_test.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(validZip))) {
            ZipEntry e = new ZipEntry("file.txt");
            zos.putNextEntry(e);
            zos.write("data".getBytes());
            zos.closeEntry();
        }

        Path destDir = tempDir.resolve("dest_fail_closed");
        Files.createDirectories(destDir);

        if (!ArchiveUtil.isSecureDirectoryStreamSupported(destDir)) {
            SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                    ArchiveUtil.extractZipSafely(validZip, destDir));
            assertEquals("UNSUPPORTED_FILESYSTEM", ex.getErrorCode(),
                    "Extraction on unsupported SDS platform must fail closed with UNSUPPORTED_FILESYSTEM before commit");
            assertFalse(Files.exists(destDir.resolve("file.txt")),
                    "No destination file should be committed on unsupported filesystem provider");
        }
    }

    /**
     * 11. testPathTraversalRejected
     * Zip-slip path traversal attempt rejected with SECURITY_VIOLATION.
     */
    @Test
    void testPathTraversalRejected(@TempDir Path tempDir) throws Exception {
        File maliciousZip = tempDir.resolve("traversal.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(maliciousZip))) {
            ZipEntry slipEntry = new ZipEntry("../../escaped_secret.txt");
            zos.putNextEntry(slipEntry);
            zos.write("PWNED".getBytes());
            zos.closeEntry();
        }

        Path destDir = tempDir.resolve("extract_slip");
        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(maliciousZip, destDir));
        assertEquals("SECURITY_VIOLATION", ex.getErrorCode());
        assertFalse(Files.exists(tempDir.resolve("escaped_secret.txt")));
    }

    /**
     * 12. testDuplicateArchiveEntriesRejected
     * Archive with duplicate entries must be rejected immediately with SECURITY_VIOLATION.
     */
    @Test
    void testDuplicateArchiveEntriesRejected(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("duplicate_entries.zip").toFile();
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(zipFile)) {
            ZipArchiveEntry entry1 = new ZipArchiveEntry("data/file.txt");
            zaos.putArchiveEntry(entry1);
            zaos.write("first copy".getBytes());
            zaos.closeArchiveEntry();

            ZipArchiveEntry entry2 = new ZipArchiveEntry("data/file.txt");
            zaos.putArchiveEntry(entry2);
            zaos.write("duplicate collision copy".getBytes());
            zaos.closeArchiveEntry();
        }

        Path destDir = tempDir.resolve("extract_dups");
        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(zipFile, destDir));
        assertEquals("SECURITY_VIOLATION", ex.getErrorCode());
    }

    /**
     * 13. testArchiveQuotaLimitsEnforced
     * Archive resource quotas (entry count, file size, total size, compression ratio) are strictly enforced.
     */
    @Test
    void testArchiveQuotaLimitsEnforced(@TempDir Path tempDir) throws Exception {
        // A. Entry limit breach
        int originalLimit = ArchiveUtil.MAX_ENTRIES;
        ArchiveUtil.MAX_ENTRIES = 5;
        try {
            File bombZip = tempDir.resolve("entry_bomb.zip").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(bombZip))) {
                for (int i = 0; i < 10; i++) {
                    ZipEntry entry = new ZipEntry("entry_" + i + ".txt");
                    zos.putNextEntry(entry);
                    zos.write(("Data " + i).getBytes());
                    zos.closeEntry();
                }
            }
            Path destDir = tempDir.resolve("extract_bomb");
            Files.createDirectories(destDir);
            SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                    ArchiveUtil.extractZipSafely(bombZip, destDir));
            assertEquals("ZIP_BOMB_DETECTED", ex.getErrorCode());
        } finally {
            ArchiveUtil.MAX_ENTRIES = originalLimit;
        }

        // B. Total size limit breach
        long originalTotalLimit = ArchiveUtil.MAX_TOTAL_SIZE;
        ArchiveUtil.MAX_TOTAL_SIZE = 1000L;
        try {
            File bigZip = tempDir.resolve("oversized_total.zip").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(bigZip))) {
                ZipEntry entry = new ZipEntry("big_total.txt");
                zos.putNextEntry(entry);
                zos.write(new byte[5000]);
                zos.closeEntry();
            }
            Path destDir = tempDir.resolve("extract_oversized_total");
            Files.createDirectories(destDir);
            SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                    ArchiveUtil.extractZipSafely(bigZip, destDir));
            assertEquals("ZIP_BOMB_DETECTED", ex.getErrorCode());
        } finally {
            ArchiveUtil.MAX_TOTAL_SIZE = originalTotalLimit;
        }

        // C. Compression ratio breach
        File ratioBomb = tempDir.resolve("ratio_bomb.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(ratioBomb))) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            ZipEntry entry = new ZipEntry("zeros.bin");
            zos.putNextEntry(entry);
            zos.write(new byte[8192]); // 8KB of zeros compresses to ~40 bytes (>100:1 ratio)
            zos.closeEntry();
        }
        Path destDir = tempDir.resolve("extract_ratio");
        Files.createDirectories(destDir);
        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(ratioBomb, destDir));
        assertEquals("ZIP_BOMB_DETECTED", ex.getErrorCode());
    }

    // =========================================================================
    // PRESERVED FUNCTIONAL & REGRESSION TESTS
    // =========================================================================

    @Test
    void testValidZipExtraction(@TempDir Path tempDir) throws Exception {
        File zipFile = tempDir.resolve("valid.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry entry = new ZipEntry("valid_file.txt");
            zos.putNextEntry(entry);
            zos.write("Valid Content 2026".getBytes());
            zos.closeEntry();
        }

        assertTrue(ArchiveUtil.hasValidZipMagicBytes(zipFile));
        Path destDir = tempDir.resolve("extracted_valid");
        Files.createDirectories(destDir);

        if (ArchiveUtil.isSecureDirectoryStreamSupported(destDir)) {
            Path result = ArchiveUtil.extractZipSafely(zipFile, destDir);
            assertNotNull(result);
            assertTrue(Files.exists(destDir.resolve("valid_file.txt")));
            assertEquals("Valid Content 2026", Files.readString(destDir.resolve("valid_file.txt")));
        } else {
            SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                    ArchiveUtil.extractZipSafely(zipFile, destDir));
            assertEquals("UNSUPPORTED_FILESYSTEM", ex.getErrorCode());
            assertFalse(Files.exists(destDir.resolve("valid_file.txt")),
                    "Verify that no files were committed to the destination in the unsupported case");
        }
    }

    @Test
    void testInvalidMagicBytesRejection(@TempDir Path tempDir) throws Exception {
        File fakeZip = tempDir.resolve("fake.zip").toFile();
        try (FileWriter fw = new FileWriter(fakeZip)) {
            fw.write("NOT_A_ZIP_HEADER_DATA");
        }

        assertFalse(ArchiveUtil.hasValidZipMagicBytes(fakeZip));
        Path destDir = tempDir.resolve("extracted_fake");

        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(fakeZip, destDir));
        assertEquals("INVALID_FILE_TYPE", ex.getErrorCode());
        assertFalse(Files.exists(destDir));
    }

    @Test
    void testAbsolutePathTraversalBlocked(@TempDir Path tempDir) throws Exception {
        File absZip = tempDir.resolve("absolute_path.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(absZip))) {
            ZipEntry absEntry = new ZipEntry("/etc/shadow_exploit");
            zos.putNextEntry(absEntry);
            zos.write("EXPLOIT".getBytes());
            zos.closeEntry();
        }

        Path destDir = tempDir.resolve("extract_abs");
        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(absZip, destDir));
        assertEquals("SECURITY_VIOLATION", ex.getErrorCode());
        assertFalse(Files.exists(destDir));
    }

    @Test
    void testSymlinkArchiveEntryDetectedAndBlocked(@TempDir Path tempDir) throws Exception {
        File symlinkZip = tempDir.resolve("symlink_entry.zip").toFile();
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(symlinkZip)) {
            ZipArchiveEntry entry = new ZipArchiveEntry("symlink_pointer");
            entry.setUnixMode(0120777); // S_IFLNK
            zaos.putArchiveEntry(entry);
            zaos.write("../../etc/passwd".getBytes());
            zaos.closeArchiveEntry();
        }

        Path destDir = tempDir.resolve("extract_symlink");
        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(symlinkZip, destDir));
        assertEquals("SECURITY_VIOLATION", ex.getErrorCode());
        assertFalse(Files.exists(destDir));
    }

    @Test
    void testNullByteFilenameBlocked(@TempDir Path tempDir) throws Exception {
        File nullByteZip = tempDir.resolve("null_byte.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(nullByteZip))) {
            ZipEntry nullEntry = new ZipEntry("test\0exploit.txt");
            zos.putNextEntry(nullEntry);
            zos.write("TEST".getBytes());
            zos.closeEntry();
        }

        Path destDir = tempDir.resolve("extract_null");
        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(nullByteZip, destDir));
        assertEquals("SECURITY_VIOLATION", ex.getErrorCode());
        assertFalse(Files.exists(destDir));
    }

    @Test
    void testOversizedIndividualFileBlocked(@TempDir Path tempDir) throws Exception {
        long originalLimit = ArchiveUtil.MAX_SINGLE_ENTRY_SIZE;
        ArchiveUtil.MAX_SINGLE_ENTRY_SIZE = 1000L;
        try {
            File bigZip = tempDir.resolve("oversized_file.zip").toFile();
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(bigZip))) {
                ZipEntry entry = new ZipEntry("big_single.txt");
                zos.putNextEntry(entry);
                zos.write(new byte[2000]);
                zos.closeEntry();
            }
            Path destDir = tempDir.resolve("extract_oversized_file");
            Files.createDirectories(destDir);
            SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                    ArchiveUtil.extractZipSafely(bigZip, destDir));
            assertEquals("ZIP_BOMB_DETECTED", ex.getErrorCode());
        } finally {
            ArchiveUtil.MAX_SINGLE_ENTRY_SIZE = originalLimit;
        }
    }

    @Test
    void testSmallEntryCompressionRatioViolationBlocked(@TempDir Path tempDir) throws Exception {
        File smallRatioZip = tempDir.resolve("small_ratio.zip").toFile();
        try (ZipArchiveOutputStream zaos = new ZipArchiveOutputStream(smallRatioZip)) {
            zaos.setLevel(Deflater.BEST_COMPRESSION);
            ZipArchiveEntry entry = new ZipArchiveEntry("small_zeros.bin");
            zaos.putArchiveEntry(entry);
            zaos.write(new byte[4096]);
            zaos.closeArchiveEntry();
        }

        Path destDir = tempDir.resolve("extract_small_ratio");
        Files.createDirectories(destDir);
        SentinelCvException ex = assertThrows(SentinelCvException.class, () ->
                ArchiveUtil.extractZipSafely(smallRatioZip, destDir));
        assertEquals("ZIP_BOMB_DETECTED", ex.getErrorCode());
    }
}
