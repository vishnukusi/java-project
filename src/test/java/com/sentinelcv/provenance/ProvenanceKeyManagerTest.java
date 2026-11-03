package com.sentinelcv.provenance;

import com.sentinelcv.config.ApplicationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ProvenanceKeyManagerTest {

    @TempDir
    Path tempDir;

    private ApplicationConfig applicationConfig;

    @BeforeEach
    void setUp() {
        applicationConfig = Mockito.mock(ApplicationConfig.class);
        when(applicationConfig.getDataDir()).thenReturn(tempDir.toString());
    }

    @Test
    void testMissingKeyReturnsEmpty() {
        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, applicationConfig);
        Optional<String> keyOpt = manager.getVerificationKey();
        assertTrue(keyOpt.isEmpty(), "Missing key must return Optional.empty()");
    }

    @Test
    void testConfiguredPropertyPrecedence() {
        String testPropKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        ProvenanceKeyManager manager = new ProvenanceKeyManager(testPropKey, applicationConfig);
        Optional<String> keyOpt = manager.getVerificationKey();
        assertTrue(keyOpt.isPresent());
        assertEquals(testPropKey, keyOpt.get());
    }

    @Test
    void testKeyGenerationCreates256BitKey() throws IOException {
        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, applicationConfig);
        String generatedKey = manager.getOrCreateLocalKey();

        assertNotNull(generatedKey);
        // 32 bytes in hex = 64 hex characters
        assertEquals(64, generatedKey.length(), "Key should be 256 bits (64 hex characters)");
        assertTrue(generatedKey.matches("^[0-9a-fA-F]{64}$"), "Key must be valid hexadecimal string");

        Path keyFile = manager.getLocalKeyFilePath();
        assertTrue(Files.exists(keyFile), "Key file must exist on disk");
        String content = Files.readString(keyFile).trim();
        assertEquals(generatedKey, content);
    }

    @Test
    void testExistingKeyReuseDoesNotOverwrite() {
        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, applicationConfig);
        String firstKey = manager.getOrCreateLocalKey();
        String secondKey = manager.getOrCreateLocalKey();

        assertEquals(firstKey, secondKey, "Subsequent calls must reuse existing key");

        // Create a new manager pointing to same directory
        ProvenanceKeyManager manager2 = new ProvenanceKeyManager(null, applicationConfig);
        Optional<String> loadedKey = manager2.getVerificationKey();
        assertTrue(loadedKey.isPresent());
        assertEquals(firstKey, loadedKey.get());
    }

    @Test
    void testCorruptKeyThrowsExceptionAndRequiresOperatorAction() throws IOException {
        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, applicationConfig);
        Path keyFile = manager.getLocalKeyFilePath();
        Files.createDirectories(keyFile.getParent());
        // Write corrupt key (short length < 32)
        Files.writeString(keyFile, "corrupted_short_key");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            manager.getVerificationKey();
        });

        assertTrue(ex.getMessage().contains("corrupt") || ex.getMessage().contains("insufficient entropy"));
        assertTrue(ex.getMessage().contains("Explicit operator action required"));

        // Verify that getOrCreateLocalKey does NOT overwrite corrupt key
        assertThrows(IllegalStateException.class, () -> {
            manager.getOrCreateLocalKey();
        });

        // Content remains the corrupt key, not overwritten
        assertEquals("corrupted_short_key", Files.readString(keyFile).trim());
    }

    @Test
    void testConcurrentInitialization() throws Exception {
        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, applicationConfig);
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<String>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(manager::getOrCreateLocalKey);
        }

        List<Future<String>> results = executor.invokeAll(tasks);
        executor.shutdown();

        List<String> keys = new ArrayList<>();
        for (Future<String> future : results) {
            keys.add(future.get());
        }

        // All threads must get the exact same key
        String expectedKey = keys.get(0);
        assertNotNull(expectedKey);
        for (String k : keys) {
            assertEquals(expectedKey, k, "All concurrent threads must receive identical key");
        }
    }

    @Test
    void testPosixPermissionsWhereSupported() throws IOException {
        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, applicationConfig);
        manager.getOrCreateLocalKey();
        Path keyFile = manager.getLocalKeyFilePath();

        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(keyFile);
            assertTrue(perms.contains(PosixFilePermission.OWNER_READ), "Owner must have read");
            assertTrue(perms.contains(PosixFilePermission.OWNER_WRITE), "Owner must have write");
            assertFalse(perms.contains(PosixFilePermission.GROUP_READ), "Group must NOT have read");
            assertFalse(perms.contains(PosixFilePermission.GROUP_WRITE), "Group must NOT have write");
            assertFalse(perms.contains(PosixFilePermission.OTHERS_READ), "Others must NOT have read");
            assertFalse(perms.contains(PosixFilePermission.OTHERS_WRITE), "Others must NOT have write");
        }
    }

    @Test
    void testNoSecretLeakageInExceptions() {
        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, applicationConfig);
        Path keyFile = manager.getLocalKeyFilePath();

        try {
            Files.createDirectories(keyFile.getParent());
            String sensitiveSecretAttempt = "bad123";
            Files.writeString(keyFile, sensitiveSecretAttempt);

            IllegalStateException ex = assertThrows(IllegalStateException.class, manager::getVerificationKey);
            assertFalse(ex.getMessage().contains(sensitiveSecretAttempt), "Exception message must not leak file content");
        } catch (IOException e) {
            fail("Setup failed: " + e.getMessage());
        }
    }

    @Test
    void testInvalidHexCharactersInConfiguredKeyThrowsIllegalStateException() {
        // 64 characters long but contains non-hex characters "zz"
        String invalidHexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdzz";
        ProvenanceKeyManager manager = new ProvenanceKeyManager(invalidHexKey, applicationConfig);

        IllegalStateException ex = assertThrows(IllegalStateException.class, manager::getVerificationKey);
        assertTrue(ex.getMessage().contains("corrupt, weak, or malformed"));
        assertFalse(ex.getMessage().contains("zz"), "Key secret value must never be logged or leaked");
    }

    @Test
    void testShortConfiguredKeyThrowsIllegalStateException() {
        String shortKey = "abcdef123456";
        ProvenanceKeyManager manager = new ProvenanceKeyManager(shortKey, applicationConfig);

        IllegalStateException ex = assertThrows(IllegalStateException.class, manager::getVerificationKey);
        assertTrue(ex.getMessage().contains("must be exactly 64 hexadecimal characters"));
    }

    @Test
    void testKeyDirectoryPosixPermissions0700() throws IOException {
        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, applicationConfig);
        manager.getOrCreateLocalKey();
        Path keyFile = manager.getLocalKeyFilePath();
        Path keyDir = keyFile.getParent();

        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Set<PosixFilePermission> dirPerms = Files.getPosixFilePermissions(keyDir);
            assertTrue(dirPerms.contains(PosixFilePermission.OWNER_READ));
            assertTrue(dirPerms.contains(PosixFilePermission.OWNER_WRITE));
            assertTrue(dirPerms.contains(PosixFilePermission.OWNER_EXECUTE));
            assertFalse(dirPerms.contains(PosixFilePermission.GROUP_READ));
            assertFalse(dirPerms.contains(PosixFilePermission.OTHERS_READ));
        }
    }

    @Test
    void testSymlinkedKeyFileRejected(@TempDir Path testDir) throws Exception {
        Path realKey = testDir.resolve("real_key.key");
        String validKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        Files.writeString(realKey, validKey);

        ApplicationConfig config = Mockito.mock(ApplicationConfig.class);
        when(config.getDataDir()).thenReturn(testDir.toString());

        Path keyDir = testDir.resolve("keys");
        Files.createDirectories(keyDir);
        Path symlinkKeyFile = keyDir.resolve("provenance.key");

        try {
            Files.createSymbolicLink(symlinkKeyFile, realKey);
        } catch (UnsupportedOperationException | IOException e) {
            return; // Platform doesn't support symlinks
        }

        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, config);
        IllegalStateException ex = assertThrows(IllegalStateException.class, manager::getVerificationKey);
        assertTrue(ex.getMessage().contains("symbolic link"),
                "Symlinked key file must be strictly rejected with IllegalStateException");
    }

    @Test
    void testSymlinkedKeyDirectoryRejected(@TempDir Path testDir) throws Exception {
        Path realDir = testDir.resolve("real_keys_dir");
        Files.createDirectories(realDir);
        String validKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        Files.writeString(realDir.resolve("provenance.key"), validKey);

        ApplicationConfig config = Mockito.mock(ApplicationConfig.class);
        when(config.getDataDir()).thenReturn(testDir.toString());

        Path symlinkKeysDir = testDir.resolve("keys");
        try {
            Files.createSymbolicLink(symlinkKeysDir, realDir);
        } catch (UnsupportedOperationException | IOException e) {
            return;
        }

        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, config);
        IllegalStateException ex = assertThrows(IllegalStateException.class, manager::getVerificationKey);
        assertTrue(ex.getMessage().contains("symbolic link"),
                "Symlinked key directory must be strictly rejected with IllegalStateException");
    }

    @Test
    void testExistingKeyPermissionsAutoCorrectedOrEnforced(@TempDir Path testDir) throws Exception {
        ApplicationConfig config = Mockito.mock(ApplicationConfig.class);
        when(config.getDataDir()).thenReturn(testDir.toString());

        ProvenanceKeyManager manager = new ProvenanceKeyManager(null, config);
        String key = manager.getOrCreateLocalKey();
        Path keyFile = manager.getLocalKeyFilePath();

        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            // Set overly permissive permissions (0666)
            Set<PosixFilePermission> openPerms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE
            );
            Files.setPosixFilePermissions(keyFile, openPerms);

            // Accessing key should auto-tighten permissions back to 0600
            Optional<String> loaded = manager.getVerificationKey();
            assertTrue(loaded.isPresent());
            assertEquals(key, loaded.get());

            Set<PosixFilePermission> correctedPerms = Files.getPosixFilePermissions(keyFile);
            assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), correctedPerms,
                    "Insecure permissions must be auto-corrected to 0600");
        }
    }

    @Test
    void testWriteKeyFileSecurelyProtectsParentAndFile(@TempDir Path testDir) throws Exception {
        Path keyFile = testDir.resolve("secure_dir").resolve("secret.key");
        String testKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        ProvenanceKeyManager.writeKeyFileSecurely(keyFile, testKey);
        assertTrue(Files.exists(keyFile));

        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Set<PosixFilePermission> dirPerms = Files.getPosixFilePermissions(keyFile.getParent());
            assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE), dirPerms);

            Set<PosixFilePermission> filePerms = Files.getPosixFilePermissions(keyFile);
            assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), filePerms);
        }
    }
}
