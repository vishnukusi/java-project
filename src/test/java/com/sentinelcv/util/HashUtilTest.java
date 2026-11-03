package com.sentinelcv.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {

    @Test
    void testSha256String() {
        String input = "SentinelCV";
        String hash = HashUtil.sha256(input);
        assertNotNull(hash);
        assertEquals(64, hash.length());
        // Deterministic check
        assertEquals(hash, HashUtil.sha256(input));
    }

    @Test
    void testSha256File(@TempDir Path tempDir) throws IOException {
        File file = tempDir.resolve("test.txt").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("Hello Computer Vision Security");
        }

        String hash = HashUtil.sha256(file);
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("^[a-f0-9]{64}$"));
    }

    @Test
    void testSha256NullAndEmpty() {
        assertEquals("", HashUtil.sha256((String) null));
        assertEquals("", HashUtil.sha256((byte[]) null));
        assertEquals(64, HashUtil.sha256("").length());
    }
}
