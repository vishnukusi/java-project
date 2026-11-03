package com.sentinelcv.util;

import com.sentinelcv.exception.SentinelCvException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Utility for cryptographic hashing operations using SHA-256.
 * Provides file hashing, byte-array hashing, and collision-resistant canonical dataset manifest hashing.
 * Enforces fail-closed validation: no implicit file exclusions, symlink rejection,
 * deterministic length-prefixed binary manifest serialization, and concurrent mutation detection.
 */
public final class HashUtil {

    private static final String SHA_256 = "SHA-256";
    private static final int BUFFER_SIZE = 8192;

    // Canonical Manifest Header constants
    private static final byte[] MANIFEST_MAGIC = new byte[] { 0x53, 0x43, 0x56, 0x4D }; // "SCVM"
    private static final short MANIFEST_VERSION = 1; // Schema Version 1
    private static final byte ENTRY_TYPE_REGULAR_FILE = 0x01;

    // Test interception hook to simulate concurrent filesystem mutations deterministically
    public static volatile Consumer<String> HASHING_INTERCEPTOR = null;

    private HashUtil() {}

    /**
     * Calculates the SHA-256 hash of a file using buffered streaming to prevent memory exhaustion.
     */
    public static String sha256(File file) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File does not exist or is not a regular file: " + file.getAbsolutePath());
        }
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            return sha256(is);
        }
    }

    /**
     * Calculates the SHA-256 hash of an InputStream.
     */
    public static String sha256(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Calculates the SHA-256 hash of a String.
     */
    public static String sha256(String input) {
        if (input == null) {
            return "";
        }
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Calculates the SHA-256 hash of a byte array.
     */
    public static String sha256(byte[] data) {
        if (data == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            return bytesToHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts a byte array to its lowercase hexadecimal representation.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String calculateDatasetDirectoryHash(File dir) throws IOException {
        if (dir == null) {
            throw new IllegalArgumentException("Dataset directory cannot be null.");
        }

        Path root = dir.toPath().toAbsolutePath().normalize();
        BasicFileAttributes rootBefore = readAttributes(root);

        if (!rootBefore.isDirectory() || rootBefore.isSymbolicLink()) {
            throw new SentinelCvException(
                    "SECURITY_VIOLATION",
                    "Dataset root must be a non-symlink directory: " + root
            );
        }

        Map<String, DatasetEntrySnapshot> first = portableScan(root);
        Map<String, DatasetEntrySnapshot> second = portableScan(root);

        if (!first.equals(second)) {
            throw new SentinelCvException(
                    "CONCURRENT_MUTATION_DETECTED",
                    "Dataset changed between integrity scans."
            );
        }

        BasicFileAttributes rootAfter = readAttributes(root);
        verifyStableDirectory(rootBefore, rootAfter, root.toString());

        try {
            MessageDigest manifest = MessageDigest.getInstance(SHA_256);
            manifest.update(MANIFEST_MAGIC);

            long count = first.values().stream()
                    .filter(e -> !e.directory())
                    .count();

            if (count > Integer.MAX_VALUE) {
                throw new IOException("Too many dataset files.");
            }

            ByteBuffer header = ByteBuffer.allocate(6);
            header.putShort(MANIFEST_VERSION);
            header.putInt((int) count);
            manifest.update(header.array());

            for (Map.Entry<String, DatasetEntrySnapshot> item : first.entrySet()) {
                DatasetEntrySnapshot entry = item.getValue();

                if (entry.directory()) {
                    continue;
                }

                byte[] path = item.getKey().getBytes(StandardCharsets.UTF_8);

                ByteBuffer metadata = ByteBuffer.allocate(
                        1 + Integer.BYTES + path.length + Long.BYTES
                );

                metadata.put(ENTRY_TYPE_REGULAR_FILE);
                metadata.putInt(path.length);
                metadata.put(path);
                metadata.putLong(entry.size());

                manifest.update(metadata.array());
                manifest.update(HexFormat.of().parseHex(entry.sha256()));
            }

            return bytesToHex(manifest.digest());

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable.", e);
        }
    }

    private record DatasetEntrySnapshot(
            boolean directory,
            long size,
            long modifiedMillis,
            long creationMillis,
            String fileKey,
            String sha256
    ) {}

    private static BasicFileAttributes readAttributes(Path path) throws IOException {
        return Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
    }

    private static Map<String, DatasetEntrySnapshot> portableScan(Path root)
            throws IOException {

        Map<String, DatasetEntrySnapshot> result = new TreeMap<>();

        Files.walkFileTree(
                root,
                EnumSet.noneOf(FileVisitOption.class),
                Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory,
                            BasicFileAttributes attributes
                    ) throws IOException {

                        if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
                            throw new SentinelCvException(
                                    "SECURITY_VIOLATION",
                                    "Invalid directory: " + directory
                            );
                        }

                        if (!directory.equals(root)) {
                            result.put(
                                    relativePath(root, directory),
                                    snapshotDirectory(attributes)
                            );
                        }

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file,
                            BasicFileAttributes attributes
                    ) throws IOException {

                        if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                            throw new SentinelCvException(
                                    "SECURITY_VIOLATION",
                                    "Symlink or special file detected: " + file
                            );
                        }

                        String relative = relativePath(root, file);

                        // Capture the file state before the test hook or hashing begins.
                        BasicFileAttributes before = attributes;

                        if (before.isSymbolicLink() || !before.isRegularFile()) {
                            throw new SentinelCvException(
                                    "SECURITY_VIOLATION",
                                    "Invalid file detected: " + relative
                            );
                        }

                        Consumer<String> interceptor = HASHING_INTERCEPTOR;
                        if (interceptor != null) {
                            interceptor.accept(relative);
                        }

                        String hash = hashFileNoFollow(file);

                        BasicFileAttributes after = readAttributes(file);
                        verifyStableFile(before, after, relative);

                        result.put(
                                relative,
                                new DatasetEntrySnapshot(
                                        false,
                                        after.size(),
                                        after.lastModifiedTime().toMillis(),
                                        after.creationTime().toMillis(),
                                        fileKey(after),
                                        hash
                                )
                        );

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(
                            Path file,
                            IOException exception
                    ) throws IOException {

                        throw new SentinelCvException(
                                "IO_ERROR",
                                "Cannot inspect dataset entry: " + file
                                        + ": " + exception.getMessage()
                        );
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(
                            Path directory,
                            IOException exception
                    ) throws IOException {

                        if (exception != null) {
                            throw exception;
                        }

                        if (!directory.equals(root)) {
                            String relative = relativePath(root, directory);
                            DatasetEntrySnapshot before = result.get(relative);
                            BasicFileAttributes after = readAttributes(directory);

                            if (before == null) {
                                throw new SentinelCvException(
                                        "CONCURRENT_MUTATION_DETECTED",
                                        "Directory disappeared: " + relative
                                );
                            }

                            verifyStableDirectory(before, after, relative);
                        }

                        return FileVisitResult.CONTINUE;
                    }
                }
        );

        return result;
    }

    private static DatasetEntrySnapshot snapshotDirectory(
            BasicFileAttributes attributes
    ) {
        return new DatasetEntrySnapshot(
                true,
                0,
                attributes.lastModifiedTime().toMillis(),
                attributes.creationTime().toMillis(),
                fileKey(attributes),
                null
        );
    }

    private static String hashFileNoFollow(Path file) throws IOException {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable.", e);
        }

        long bytesRead = 0;

        Set<OpenOption> options = Set.of(
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS
        );

        try (SeekableByteChannel channel = Files.newByteChannel(file, options)) {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

            while (true) {
                int count = channel.read(buffer);

                if (count < 0) {
                    break;
                }

                if (count == 0) {
                    continue;
                }

                buffer.flip();
                bytesRead += buffer.remaining();
                digest.update(buffer);
                buffer.clear();
            }
        }

        if (bytesRead != readAttributes(file).size()) {
            throw new SentinelCvException(
                    "CONCURRENT_MUTATION_DETECTED",
                    "File size changed during hashing: " + file
            );
        }

        return bytesToHex(digest.digest());
    }

    private static String relativePath(Path root, Path path) {
        return root.relativize(path).toString()
                .replace(File.separatorChar, '/');
    }

    private static String fileKey(BasicFileAttributes attributes) {
        Object key = attributes.fileKey();
        return key == null ? null : key.toString();
    }

    private static void verifyStableFile(
            BasicFileAttributes before,
            BasicFileAttributes after,
            String relative
    ) {
        boolean identityChanged =
                before.fileKey() != null
                        && after.fileKey() != null
                        && !Objects.equals(before.fileKey(), after.fileKey());

        if (after.isSymbolicLink()
                || !after.isRegularFile()
                || identityChanged
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !before.creationTime().equals(after.creationTime())) {

        throw new SentinelCvException(
                "CONCURRENT_MUTATION_DETECTED",
                "File changed during hashing: " + relative
        );
    }
}

    private static void verifyStableDirectory(
            BasicFileAttributes before,
            BasicFileAttributes after,
            String relative
    ) {
        boolean identityChanged =
                before.fileKey() != null
                        && after.fileKey() != null
                        && !Objects.equals(before.fileKey(), after.fileKey());

        if (after.isSymbolicLink()
                || !after.isDirectory()
                || identityChanged
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !before.creationTime().equals(after.creationTime())) {

        throw new SentinelCvException(
                "CONCURRENT_MUTATION_DETECTED",
                "Directory changed during hashing: " + relative
        );
    }
}

    private static void verifyStableDirectory(
            DatasetEntrySnapshot before,
            BasicFileAttributes after,
            String relative
    ) {
        boolean identityChanged =
                before.fileKey() != null
                        && after.fileKey() != null
                        && !Objects.equals(before.fileKey(), fileKey(after));

        if (after.isSymbolicLink()
                || !after.isDirectory()
                || identityChanged
                || before.modifiedMillis() != after.lastModifiedTime().toMillis()
                || before.creationMillis() != after.creationTime().toMillis()) {

        throw new SentinelCvException(
                "CONCURRENT_MUTATION_DETECTED",
                "Directory changed during hashing: " + relative
        );
    }
}
}
