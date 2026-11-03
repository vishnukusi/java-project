package com.sentinelcv.provenance;

import com.sentinelcv.config.ApplicationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Manages cryptographic verification keys securely without hardcoded secrets.
 * Enforces strict 256-bit hexadecimal keys (exactly 64 valid hex characters).
 *
 * Priority order:
 * 1. Explicitly configured property (`sentinelcv.provenance.hmac-key`)
 * 2. Environment variable (`SENTINELCV_HMAC_KEY`)
 * 3. Secure local key store file (`./data/keys/provenance.key`)
 */
@Component
public class ProvenanceKeyManager {

    private static final Logger logger = LoggerFactory.getLogger(ProvenanceKeyManager.class);
    public static final int KEY_LENGTH_BYTES = 32; // 256-bit key
    public static final int REQUIRED_HEX_LENGTH = 64; // Exactly 64 hex characters
    public static final String KEY_HEX_PATTERN = "^[0-9a-fA-F]{64}$";
    private static final Object INIT_LOCK = new Object();

    private final String configuredKey;
    private final ApplicationConfig applicationConfig;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProvenanceKeyManager(@Value("${sentinelcv.provenance.hmac-key:}") String configuredKey,
                                ApplicationConfig applicationConfig) {
        this.configuredKey = configuredKey;
        this.applicationConfig = applicationConfig;
    }

    /**
     * Validates that a key consists of exactly 64 valid hexadecimal characters.
     * Never leaks the actual secret key in error messages.
     */
    public static void validateKeyFormat(String key, String sourceName) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("Provenance key from " + sourceName + " is empty or null.");
        }
        String trimmed = key.trim();
        if (trimmed.length() != REQUIRED_HEX_LENGTH || !trimmed.matches(KEY_HEX_PATTERN)) {
            throw new IllegalStateException("Provenance key from " + sourceName +
                    " is corrupt, weak, or malformed: must be exactly " + REQUIRED_HEX_LENGTH +
                    " hexadecimal characters (256-bit key). Received length=" + trimmed.length() +
                    ". Explicit operator action required.");
        }
    }

    /**
     * Retrieves the active HMAC verification key if available.
     * Returns Optional.empty() if no verification key is configured or installed.
     * Throws IllegalStateException if an explicitly provided key or local key file is invalid.
     */
    public Optional<String> getVerificationKey() {
        // 1. Configured via application.properties / YAML
        if (configuredKey != null && !configuredKey.trim().isEmpty()) {
            validateKeyFormat(configuredKey, "configuration property 'sentinelcv.provenance.hmac-key'");
            return Optional.of(configuredKey.trim().toLowerCase());
        }

        // 2. Configured via environment variable
        String envKey = System.getenv("SENTINELCV_HMAC_KEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            validateKeyFormat(envKey, "environment variable 'SENTINELCV_HMAC_KEY'");
            return Optional.of(envKey.trim().toLowerCase());
        }

        // 3. Local key store file
        Path keyFile = getLocalKeyFilePath();
        if (Files.exists(keyFile, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(keyFile)) {
                throw new IllegalStateException("Local provenance key file is a symbolic link: " + keyFile);
            }
            Path parent = keyFile.getParent();
            if (parent != null && Files.isSymbolicLink(parent)) {
                throw new IllegalStateException("Local provenance key directory is a symbolic link: " + parent);
            }
            return Optional.of(readAndValidateLocalKey(keyFile));
        }

        return Optional.empty();
    }

    /**
     * Initializes or returns the local secure key. If none exists, creates a fresh
     * cryptographically secure local key atomically with 0600 POSIX permissions,
     * within a directory restricted to 0700 POSIX permissions.
     * Thread-safe and concurrency-protected.
     */
    public String getOrCreateLocalKey() {
        synchronized (INIT_LOCK) {
            Optional<String> existing = getVerificationKey();
            if (existing.isPresent()) {
                return existing.get();
            }

            Path keyPath = getLocalKeyFilePath();
            try {
                // Generate 256-bit secure key
                byte[] keyBytes = new byte[KEY_LENGTH_BYTES];
                secureRandom.nextBytes(keyBytes);
                String newKey = HexFormat.of().formatHex(keyBytes);

                // Create with 0600 POSIX permissions atomically where supported
                writeKeyFileSecurely(keyPath, newKey);
                logger.info("Generated new local provenance HMAC key in secure keystore at {}", keyPath);
                return newKey;
            } catch (IOException e) {
                logger.error("Failed to create local provenance key at {}: {}", keyPath, e.getMessage());
                throw new IllegalStateException("Could not create local provenance key: " + e.getMessage(), e);
            }
        }
    }

    private String readAndValidateLocalKey(Path keyFile) {
        if (Files.isSymbolicLink(keyFile)) {
            throw new IllegalStateException("Local provenance key file is a symbolic link: " + keyFile);
        }
        Path parent = keyFile.getParent();
        if (parent != null && Files.isSymbolicLink(parent)) {
            throw new IllegalStateException("Local provenance key directory is a symbolic link: " + parent);
        }

        try {
            if (parent != null && Files.exists(parent, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                verifyDirectoryPermissions(parent);
            }
            verifyFilePermissions(keyFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect or enforce permissions on " + keyFile + ": " + e.getMessage(), e);
        }

        try {
            String content = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
            validateKeyFormat(content, "local key file '" + keyFile + "'");
            return content.toLowerCase();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read local provenance key from " + keyFile + ": " + e.getMessage(), e);
        }
    }

    public static void writeKeyFileSecurely(Path keyPath, String keyContent) throws IOException {
        validateKeyFormat(keyContent, "generated local key");

        if (Files.exists(keyPath, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(keyPath)) {
                throw new IllegalStateException("Key path is a symbolic link: " + keyPath);
            }
            throw new IllegalStateException("Key file already exists, will not overwrite: " + keyPath);
        }

        Path parent = keyPath.getParent();
        if (parent != null) {
            if (Files.exists(parent, java.nio.file.LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(parent)) {
                throw new IllegalStateException("Key parent directory is a symbolic link: " + parent);
            }
            Files.createDirectories(parent);
            if (Files.isSymbolicLink(parent)) {
                throw new IllegalStateException("Key parent directory is a symbolic link: " + parent);
            }
            restrictDirectoryPermissions(parent);
        }

        Path tempFile = null;
        try {
            boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
            if (isPosix) {
                Set<PosixFilePermission> filePermissions = EnumSet.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE
                );
                FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(filePermissions);
                tempFile = Files.createTempFile(parent != null ? parent : Paths.get("."), "key_", ".tmp", attr);
            } else {
                tempFile = Files.createTempFile(parent != null ? parent : Paths.get("."), "key_", ".tmp");
                restrictFilePermissions(tempFile);
            }

            // Verify permissions BEFORE writing any sensitive secret bytes into the temporary file
            verifyFilePermissions(tempFile);

            // Write key bytes
            Files.writeString(tempFile, keyContent, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

            // Re-verify temp file permissions before atomic move
            verifyFilePermissions(tempFile);

            // Atomic move to target destination
            Files.move(tempFile, keyPath, StandardCopyOption.ATOMIC_MOVE);

            // Verify permissions on the final key file; fail closed if permissions mismatch
            try {
                verifyFilePermissions(keyPath);
            } catch (Exception e) {
                Files.deleteIfExists(keyPath);
                throw new IllegalStateException("Failed to enforce permissions on final key file " + keyPath + ": " + e.getMessage(), e);
            }

        } catch (IOException | RuntimeException e) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {}
            }
            throw e;
        }
    }

    public static void restrictDirectoryPermissions(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Set<String> views = FileSystems.getDefault().supportedFileAttributeViews();
        if (views.contains("posix")) {
            Set<PosixFilePermission> dirPermissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            );
            Files.setPosixFilePermissions(dir, dirPermissions);
            Set<PosixFilePermission> actual = Files.getPosixFilePermissions(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (!actual.equals(dirPermissions)) {
                throw new IllegalStateException("Failed to enforce 0700 POSIX permissions on key directory " + dir + " (actual: " + actual + ")");
            }
        } else if (views.contains("acl")) {
            enforceAclRestrictions(dir, true);
        } else {
            File pDir = dir.toFile();
            boolean r = pDir.setReadable(false, false) && pDir.setReadable(true, true);
            boolean w = pDir.setWritable(false, false) && pDir.setWritable(true, true);
            boolean x = pDir.setExecutable(false, false) && pDir.setExecutable(true, true);
            if (!r || !w || !x) {
                throw new IllegalStateException("Could not restrict directory permissions on non-POSIX filesystem for " + dir);
            }
        }
    }

    public static void restrictFilePermissions(Path file) throws IOException {
        if (file == null || !Files.exists(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Set<String> views = FileSystems.getDefault().supportedFileAttributeViews();
        if (views.contains("posix")) {
            Set<PosixFilePermission> filePermissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(file, filePermissions);
            Set<PosixFilePermission> actual = Files.getPosixFilePermissions(file, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (!actual.equals(filePermissions)) {
                throw new IllegalStateException("Failed to enforce 0600 POSIX permissions on key file " + file + " (actual: " + actual + ")");
            }
        } else if (views.contains("acl")) {
            enforceAclRestrictions(file, false);
        } else {
            File f = file.toFile();
            boolean r = f.setReadable(false, false) && f.setReadable(true, true);
            boolean w = f.setWritable(false, false) && f.setWritable(true, true);
            boolean x = f.setExecutable(false, false);
            if (!r || !w || !x) {
                throw new IllegalStateException("Could not restrict file permissions on non-POSIX filesystem for " + file);
            }
        }
    }

    public static void verifyDirectoryPermissions(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Set<String> views = FileSystems.getDefault().supportedFileAttributeViews();
        if (views.contains("posix")) {
            Set<PosixFilePermission> dirPermissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
            );
            Set<PosixFilePermission> actual = Files.getPosixFilePermissions(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (!actual.equals(dirPermissions)) {
                try {
                    Files.setPosixFilePermissions(dir, dirPermissions);
                    actual = Files.getPosixFilePermissions(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS);
                } catch (Exception e) {
                    throw new IllegalStateException("Insecure key directory permissions on " + dir + " (" + actual + ") and could not be corrected: " + e.getMessage());
                }
                if (!actual.equals(dirPermissions)) {
                    throw new IllegalStateException("Insecure key directory permissions on " + dir + " (" + actual + "). Required 0700.");
                }
            }
        } else if (views.contains("acl")) {
            verifyAclRestrictions(dir, true);
        } else {
            File pDir = dir.toFile();
            if (!pDir.canRead() || !pDir.canWrite()) {
                throw new IllegalStateException("Key directory permissions insecure on non-POSIX filesystem for " + dir);
            }
        }
    }

    public static void verifyFilePermissions(Path file) throws IOException {
        if (file == null || !Files.exists(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Set<String> views = FileSystems.getDefault().supportedFileAttributeViews();
        if (views.contains("posix")) {
            Set<PosixFilePermission> filePermissions = EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            );
            Set<PosixFilePermission> actual = Files.getPosixFilePermissions(file, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (!actual.equals(filePermissions)) {
                try {
                    Files.setPosixFilePermissions(file, filePermissions);
                    actual = Files.getPosixFilePermissions(file, java.nio.file.LinkOption.NOFOLLOW_LINKS);
                } catch (Exception e) {
                    throw new IllegalStateException("Insecure key file permissions on " + file + " (" + actual + ") and could not be corrected: " + e.getMessage());
                }
                if (!actual.equals(filePermissions)) {
                    throw new IllegalStateException("Insecure key file permissions on " + file + " (" + actual + "). Required 0600.");
                }
            }
        } else if (views.contains("acl")) {
            verifyAclRestrictions(file, false);
        } else {
            File f = file.toFile();
            if (!f.canRead() || !f.canWrite()) {
                throw new IllegalStateException("Key file permissions insecure on non-POSIX filesystem for " + file);
            }
        }
    }

    private static void enforceAclRestrictions(Path path, boolean isDirectory) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IllegalStateException("AclFileAttributeView is not available for " + path);
        }
        UserPrincipal owner = view.getOwner();
        if (owner == null) {
            throw new IllegalStateException("Could not determine file owner for ACL enforcement on " + path);
        }
        Set<AclEntryPermission> permissions = isDirectory ?
                EnumSet.of(
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.READ_NAMED_ATTRS,
                        AclEntryPermission.WRITE_NAMED_ATTRS,
                        AclEntryPermission.EXECUTE,
                        AclEntryPermission.DELETE_CHILD,
                        AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.DELETE,
                        AclEntryPermission.READ_ACL,
                        AclEntryPermission.WRITE_ACL,
                        AclEntryPermission.WRITE_OWNER,
                        AclEntryPermission.SYNCHRONIZE
                ) :
                EnumSet.of(
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.READ_NAMED_ATTRS,
                        AclEntryPermission.WRITE_NAMED_ATTRS,
                        AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.DELETE,
                        AclEntryPermission.READ_ACL,
                        AclEntryPermission.WRITE_ACL,
                        AclEntryPermission.WRITE_OWNER,
                        AclEntryPermission.SYNCHRONIZE
                );

        AclEntry entry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(permissions)
                .build();

        view.setAcl(List.of(entry));
        verifyAclRestrictions(path, isDirectory);
    }

    private static void verifyAclRestrictions(Path path, boolean isDirectory) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IllegalStateException("AclFileAttributeView is not available for " + path);
        }
        UserPrincipal owner = view.getOwner();
        List<AclEntry> acl = view.getAcl();
        if (acl == null || acl.isEmpty()) {
            throw new IllegalStateException("Empty ACL on " + path + "; expected owner-only permissions");
        }
        for (AclEntry entry : acl) {
            if (entry.type() == AclEntryType.ALLOW && !entry.principal().equals(owner)) {
                throw new IllegalStateException("Insecure ACL on " + path + ": non-owner principal " + entry.principal() + " has ALLOW entry");
            }
        }
    }

    public Path getLocalKeyFilePath() {
        return Paths.get(applicationConfig.getDataDir(), "keys", "provenance.key");
    }
}
