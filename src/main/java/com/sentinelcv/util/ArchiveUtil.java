package com.sentinelcv.util;

import com.sentinelcv.exception.SentinelCvException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility for safely extracting ZIP archives.
 * Implements strict Zip-Slip and path traversal protection, symlink traversal prevention,
 * magic byte validation, archive resource quotas (zip-bomb prevention),
 * compression ratio limits (including small entries), duplicate entry rejection,
 * collision protection (never overwrites existing destination files),
 * isolated staging, atomic or safe commit, and failure recovery that preserves unrelated pre-existing data.
 */
public final class ArchiveUtil {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveUtil.class);

    private static final int BUFFER_SIZE = 8192;
    public static int MAX_ENTRIES = 10_000;
    public static long MAX_TOTAL_SIZE = 500L * 1024 * 1024; // 500 MB uncompressed
    public static long MAX_SINGLE_ENTRY_SIZE = 100L * 1024 * 1024; // 100 MB per entry
    public static double MAX_COMPRESSION_RATIO = 100.0; // 100:1 max ratio

    // Lock registry to prevent concurrent extractions into the same destination path
    private static final ConcurrentMap<String, ReentrantLock> DESTINATION_LOCKS = new ConcurrentHashMap<>();

    // Test interception hook to simulate TOCTOU races deterministically during commit
    public static volatile java.util.function.BiConsumer<String, Path> COMMIT_INTERCEPTOR = null;

    /**
     * Checks whether the active filesystem provider for target directory supports SecureDirectoryStream.
     */
    public static boolean isSecureDirectoryStreamSupported(Path targetDirectory) {
        if (targetDirectory == null) return false;
        try {
            Path p = targetDirectory.toAbsolutePath().normalize();
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                Path parent = p.getParent();
                if (parent == null || !Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
                    return false;
                }
                p = parent;
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                return ds instanceof SecureDirectoryStream;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private ArchiveUtil() {}

    /**
     * Validates that the file has standard ZIP header magic bytes (PK\x03\x04 or PK\x05\x06 or PK\x07\x08).
     */
    public static boolean hasValidZipMagicBytes(File file) {
        if (file == null || !file.exists() || file.length() < 4) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            if (read < 4) return false;
            return (header[0] == 0x50 && header[1] == 0x4B &&
                    ((header[2] == 0x03 && header[3] == 0x04) ||
                     (header[2] == 0x05 && header[3] == 0x06) ||
                     (header[2] == 0x07 && header[3] == 0x08)));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Validates that neither the target directory nor any of its existing parent
     * components is a symbolic link, and ensures no pre-existing symlinks exist inside the destination tree.
     */
    public static void validateDestinationDirectory(Path targetDirectory) {
        if (targetDirectory == null) {
            throw new IllegalArgumentException("Target directory cannot be null");
        }
        Path normalized = targetDirectory.toAbsolutePath().normalize();

        // 1. Check if targetDirectory itself or existing parent components are symbolic links
        Path p = normalized;
        while (p != null && p.getParent() != null && !p.equals(p.getRoot())) {
            if (Files.exists(p, java.nio.file.LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(p)) {
                // Ignore top-level OS root symlinks (e.g. macOS /var -> private/var)
                if (p.getNameCount() > 1 || p.equals(normalized)) {
                    throw new SentinelCvException("SECURITY_VIOLATION",
                            "Destination directory or parent path component is a symbolic link: " + p);
                }
            }
            p = p.getParent();
        }

        // 2. Reject pre-existing symbolic links inside the destination directory tree
        if (Files.exists(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(normalized)) {
                throw new SentinelCvException("SECURITY_VIOLATION",
                        "Destination directory is a symbolic link: " + normalized);
            }
            try {
                Files.walkFileTree(normalized, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (attrs.isSymbolicLink() || Files.isSymbolicLink(dir)) {
                            throw new SentinelCvException("SECURITY_VIOLATION",
                                    "Pre-existing symbolic link directory found inside destination: " + dir);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isSymbolicLink() || Files.isSymbolicLink(file)) {
                            throw new SentinelCvException("SECURITY_VIOLATION",
                                    "Pre-existing symbolic link found inside destination: " + file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (SentinelCvException sce) {
                throw sce;
            } catch (IOException e) {
                throw new SentinelCvException("IO_ERROR", "Failed to inspect destination directory tree: " + e.getMessage());
            }
        }
    }

    /**
     * Extracts a ZIP archive safely into targetDirectory.
     * Uses an isolated temporary staging directory, validates all entries and resource quotas,
     * checks for destination collisions before committing, commits atomically where supported,
     * and guarantees rollback preserves unrelated pre-existing files on failure.
     */
    public static Path extractZipSafely(File zipFile, Path targetDirectory) throws IOException {
        if (zipFile == null || !zipFile.exists() || !zipFile.isFile()) {
            throw new IllegalArgumentException("Zip file does not exist or is not a regular file: " +
                    (zipFile != null ? zipFile.getAbsolutePath() : "null"));
        }

        // 1. Verify Magic Bytes
        if (!hasValidZipMagicBytes(zipFile)) {
            throw new SentinelCvException("INVALID_FILE_TYPE",
                    "Uploaded file is not a valid ZIP archive (missing PK magic bytes).");
        }

        // 2. Validate destination directory and parent components
        validateDestinationDirectory(targetDirectory);

        Path normalizedTarget = targetDirectory.toAbsolutePath().normalize();
        String lockKey = normalizedTarget.toString();
        ReentrantLock lock = DESTINATION_LOCKS.computeIfAbsent(lockKey, k -> new ReentrantLock());

        lock.lock();
        try {
            // Cross-process mutual exclusion via FileLock in trusted directory
            Path lockDir = Paths.get(System.getProperty("java.io.tmpdir"), "sentinelcv_locks");
            if (Files.exists(lockDir, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(lockDir)) {
                throw new SentinelCvException("SECURITY_VIOLATION", "Lock directory is a symbolic link: " + lockDir);
            }
            Files.createDirectories(lockDir);
            Path lockFilePath = lockDir.resolve("extract_" + HashUtil.sha256(lockKey) + ".lock");
            if (Files.exists(lockFilePath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(lockFilePath)) {
                throw new SentinelCvException("SECURITY_VIOLATION", "Lock file is a symbolic link: " + lockFilePath);
            }
            try (FileChannel lockChannel = FileChannel.open(lockFilePath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock fileLock = lockChannel.tryLock()) {

                if (fileLock == null) {
                    throw new SentinelCvException("CONCURRENT_EXTRACTION_CONFLICT",
                            "Concurrent extraction lock active for destination: " + normalizedTarget);
                }

                return executeSafeExtraction(zipFile, normalizedTarget);
            } catch (OverlappingFileLockException ofle) {
                throw new SentinelCvException("CONCURRENT_EXTRACTION_CONFLICT",
                        "Concurrent extraction lock active for destination: " + normalizedTarget);
            }
        } finally {
            lock.unlock();
        }
    }

    private static Path executeSafeExtraction(File zipFile, Path normalizedTarget) throws IOException {
        if (!Files.isDirectory(normalizedTarget, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(normalizedTarget)) {
            throw new SentinelCvException(
                    "SECURITY_VIOLATION",
                    "Extraction target must be an existing, non-symlink directory: "
                            + normalizedTarget
            );
        }

        Path stagingParent = normalizedTarget.getParent();

        if (stagingParent == null) {
            throw new SentinelCvException(
                    "UNSUPPORTED_FILESYSTEM",
                    "Cannot create a same-filesystem staging directory for this target."
            );
        }

        // Snapshot pre-existing files to protect them during rollback
        Set<Path> existingBefore = new HashSet<>();
        if (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) {
            try (var stream = Files.walk(normalizedTarget)) {
                stream.forEach(existingBefore::add);
            } catch (IOException ignored) {}
        }

        Path stagingDir = null;
        List<Path> createdInTargetFiles = new ArrayList<>();
        List<Path> createdInTargetDirs = new ArrayList<>();

        try {
            stagingDir = Files.createTempDirectory(
                    stagingParent,
                    ".sentinelcv_stage_"
            );

            if (!Objects.equals(Files.getFileStore(stagingDir), Files.getFileStore(normalizedTarget))) {
                throw new SentinelCvException(
                        "UNSUPPORTED_FILESYSTEM",
                        "Staging directory and destination must be on the same filesystem."
                );
            }

            Set<String> seenEntryNames = new HashSet<>();
            int entryCount = 0;
            long totalBytesExtracted = 0;
            long zipFileLength = zipFile.length();

            // Phase 1: Extract into isolated staging directory with strict quota & security validation
            try (ZipFile zip = new ZipFile(zipFile)) {
                Enumeration<ZipArchiveEntry> entries = zip.getEntries();
                while (entries.hasMoreElements()) {
                    ZipArchiveEntry entry = entries.nextElement();
                    entryCount++;
                    if (entryCount > MAX_ENTRIES) {
                        throw new SentinelCvException("ZIP_BOMB_DETECTED",
                                "Archive contains too many entries (exceeds limit of " + MAX_ENTRIES + ")");
                    }

                    // 3. Inspect ZIP entry metadata for symbolic links (Unix external attributes)
                    if (entry.isUnixSymlink() || (entry.getUnixMode() & 0170000) == 0120000) {
                        throw new SentinelCvException("SECURITY_VIOLATION",
                                "Symbolic link entry detected in archive: " + entry.getName());
                    }

                    String entryName = entry.getName();
                    // 4. Reject null-byte poisoning and absolute path traversal formats
                    if (entryName.contains("\0") || entryName.startsWith("/") || entryName.startsWith("\\") ||
                            (entryName.length() >= 2 && Character.isLetter(entryName.charAt(0)) && entryName.charAt(1) == ':')) {
                        throw new SentinelCvException("SECURITY_VIOLATION",
                                "Illegal entry name format (absolute or invalid): " + entryName);
                    }

                    // 5. Duplicate entry name check
                    String normalizedEntryName = entryName.replaceAll("/+$", "");
                    if (!normalizedEntryName.isEmpty()) {
                        if (!seenEntryNames.add(normalizedEntryName)) {
                            throw new SentinelCvException("SECURITY_VIOLATION",
                                    "Duplicate entry name detected in archive: " + entryName);
                        }
                    }

                    // 6. Resolve staging path and enforce Zip-Slip confinement
                    Path stagedPath = stagingDir.resolve(entryName).normalize();
                    if (!stagedPath.startsWith(stagingDir)) {
                        throw new SentinelCvException("SECURITY_VIOLATION",
                                "Zip-slip path traversal attempt detected in entry: " + entryName);
                    }

                    // 7. Verify no symlinks exist in staging path components
                    verifyNoSymlinks(stagedPath, stagingDir);

                    if (entry.isDirectory()) {
                        Files.createDirectories(stagedPath);
                    } else {
                        if (stagedPath.getParent() != null) {
                            Files.createDirectories(stagedPath.getParent());
                        }

                        long entryBytes = 0;
                        try (InputStream is = zip.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(stagedPath.toFile())) {
                            byte[] buffer = new byte[BUFFER_SIZE];
                            int len;
                            while ((len = is.read(buffer)) > 0) {
                                totalBytesExtracted += len;
                                entryBytes += len;

                                if (entryBytes > MAX_SINGLE_ENTRY_SIZE) {
                                    throw new SentinelCvException("ZIP_BOMB_DETECTED",
                                            "Archive entry exceeds single-file size limit (" + (MAX_SINGLE_ENTRY_SIZE / (1024 * 1024)) + "MB): " + entryName);
                                }
                                if (totalBytesExtracted > MAX_TOTAL_SIZE) {
                                    throw new SentinelCvException("ZIP_BOMB_DETECTED",
                                            "Archive extracted total size exceeds maximum allowed limit (" + (MAX_TOTAL_SIZE / (1024 * 1024)) + "MB)");
                                }
                                // Enforce total compression ratio
                                if (totalBytesExtracted > MAX_COMPRESSION_RATIO * Math.max(1L, zipFileLength)) {
                                    throw new SentinelCvException("ZIP_BOMB_DETECTED",
                                            "Archive compression ratio exceeds limit of 100:1 (extracted: " + totalBytesExtracted +
                                                    " bytes, archive: " + zipFileLength + " bytes)");
                                }
                                // Enforce entry compression ratio consistently, including small entries
                                long compSize = entry.getCompressedSize();
                                if (compSize > 0 && entryBytes > MAX_COMPRESSION_RATIO * compSize) {
                                    throw new SentinelCvException("ZIP_BOMB_DETECTED",
                                            "Archive entry compression ratio exceeds limit of 100:1: " + entryName);
                                }
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                }
            }

            // Phase 2: Check for collisions against destination directory BEFORE committing any file
            if (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS)) {
                try (var stream = Files.walk(stagingDir)) {
                    for (Path staged : stream.toList()) {
                        if (Files.isRegularFile(staged)) {
                            Path relative = stagingDir.relativize(staged);
                            Path destFile = normalizedTarget.resolve(relative).normalize();
                            if (Files.exists(destFile, LinkOption.NOFOLLOW_LINKS)) {
                                throw new SentinelCvException("DESTINATION_COLLISION",
                                        "Archive extraction rejected: destination file already exists: " + relative);
                            }
                        }
                    }
                }
            }

            // Phase 3: Commit staged files to normalizedTarget safely via SecureDirectoryStream move
            Path canonicalTarget = normalizedTarget.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (Files.isSymbolicLink(canonicalTarget)) {
                throw new SentinelCvException("SECURITY_VIOLATION",
                        "Canonical destination directory is a symbolic link: " + canonicalTarget);
            }

            if (!isSecureDirectoryStreamSupported(canonicalTarget)) {
                throw new SentinelCvException(
                        "UNSUPPORTED_FILESYSTEM",
                        "SecureDirectoryStream is required. Archive extraction is disabled "
                                + "on this filesystem."
                );
            }

            commitWithSecureDirectoryStream(
                    stagingDir,
                    canonicalTarget,
                    existingBefore,
                    createdInTargetDirs,
                    createdInTargetFiles
            );

            logger.info("Successfully extracted {} entries ({} bytes) safely to {}",
                    entryCount, totalBytesExtracted, canonicalTarget);
            return canonicalTarget;

        } catch (Throwable t) {
            // Atomic Failure Recovery: Roll back files created in target without deleting pre-existing unrelated data
            logger.warn("Cleaning up failed dataset extraction in {}: {}", normalizedTarget, t.getMessage());
            rollbackCreated(createdInTargetFiles, createdInTargetDirs, existingBefore, normalizedTarget);
            if (t instanceof IOException ioe) {
                throw ioe;
            } else if (t instanceof RuntimeException re) {
                throw re;
            } else {
                throw new IOException("Archive extraction error: " + t.getMessage(), t);
            }
        } finally {
            // Always purge the temporary staging directory
            if (stagingDir != null) {
                deleteDirectoryRecursively(stagingDir);
            }
        }
    }

    private static void commitWithSecureDirectoryStream(
            Path stagingDir,
            Path canonicalTarget,
            Set<Path> existingBefore,
            List<Path> createdInTargetDirs,
            List<Path> createdInTargetFiles
    ) throws IOException {

        try (DirectoryStream<Path> sourceStream =
                     Files.newDirectoryStream(stagingDir);
             DirectoryStream<Path> targetStream =
                     Files.newDirectoryStream(canonicalTarget)) {

            if (!(sourceStream instanceof SecureDirectoryStream<?>)
                    || !(targetStream instanceof SecureDirectoryStream<?>)) {
                throw new SentinelCvException(
                        "UNSUPPORTED_FILESYSTEM",
                        "SecureDirectoryStream is required for archive commit."
                );
            }

            @SuppressWarnings("unchecked")
            SecureDirectoryStream<Path> source =
                    (SecureDirectoryStream<Path>) sourceStream;

            @SuppressWarnings("unchecked")
            SecureDirectoryStream<Path> target =
                    (SecureDirectoryStream<Path>) targetStream;

            List<Path> names = new ArrayList<>();

            for (Path entry : source) {
                Path name = entry.getFileName();

                if (name == null
                        || name.toString().isBlank()
                        || name.getNameCount() != 1) {
                    throw new SentinelCvException(
                            "SECURITY_VIOLATION",
                            "Invalid staged entry encountered."
                    );
                }

                BasicFileAttributes attrs = Files.readAttributes(
                        entry,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS
                );

                if (attrs.isSymbolicLink()
                        || (!attrs.isDirectory() && !attrs.isRegularFile())) {
                    throw new SentinelCvException(
                            "SECURITY_VIOLATION",
                            "Staging contains an invalid filesystem object: " + name
                    );
                }

                names.add(name);
            }

            names.sort(Comparator.comparing(Path::toString));

            List<Path> moved = new ArrayList<>();

            try {
                for (Path name : names) {
                    source.move(name, target, name);
                    moved.add(name);
                }
            } catch (IOException | RuntimeException failure) {

                for (int i = moved.size() - 1; i >= 0; i--) {
                    Path name = moved.get(i);

                    try {
                        target.move(name, source, name);
                    } catch (IOException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                        logger.error(
                                "Failed to roll back staged archive entry {}: {}",
                                name,
                                rollbackFailure.getMessage()
                        );
                    }
                }

                if (failure instanceof FileAlreadyExistsException) {
                    throw new SentinelCvException(
                            "DESTINATION_COLLISION",
                            "An archive destination entry already exists."
                    );
                }

                if (failure instanceof IOException ioe) {
                    throw ioe;
                }

                throw (RuntimeException) failure;
            }
        }
    }

    private static void rollbackCreated(List<Path> createdFiles, List<Path> createdDirs, Set<Path> existingBefore, Path targetRoot) {
        // Delete all files created in this extraction
        for (Path f : createdFiles) {
            try {
                Files.deleteIfExists(f);
            } catch (IOException ignored) {}
        }
        // Delete created directories in reverse order (deepest first), only if empty and not in existingBefore
        Collections.reverse(createdDirs);
        for (Path d : createdDirs) {
            try {
                if (Files.exists(d, LinkOption.NOFOLLOW_LINKS) && !existingBefore.contains(d) && !d.equals(targetRoot)) {
                    try (var s = Files.list(d)) {
                        if (s.findAny().isEmpty()) {
                            Files.deleteIfExists(d);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
        // If targetRoot was created in this run and is now empty, delete it
        if (!existingBefore.contains(targetRoot) && Files.exists(targetRoot, LinkOption.NOFOLLOW_LINKS)) {
            try (var s = Files.list(targetRoot)) {
                if (s.findAny().isEmpty()) {
                    Files.deleteIfExists(targetRoot);
                }
            } catch (IOException ignored) {}
        }
    }

    private static void verifyNoSymlinks(Path path, Path canonicalTarget) throws SentinelCvException {
        Path current = path;
        while (current != null && !current.equals(canonicalTarget)) {
            if (Files.exists(current, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)) {
                    throw new SentinelCvException("SECURITY_VIOLATION",
                            "Symlink traversal risk detected along entry path: " + current);
                }
            }
            current = current.getParent();
        }
    }

    public static void deleteDirectoryRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.deleteIfExists(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.warn("Failed to delete directory {}: {}", dir, e.getMessage());
        }
    }
}
