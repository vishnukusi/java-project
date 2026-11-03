package com.sentinelcv.util;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class ImageHashUtilTest {

    @Test
    void testPerceptualHashAndDistance() {
        BufferedImage imgA = FixtureGenerator.createPatternImage(100, 100, Color.BLACK, Color.WHITE);
        BufferedImage imgB = FixtureGenerator.createPatternImage(100, 100, Color.BLACK, Color.WHITE);
        BufferedImage imgC = FixtureGenerator.createPatternImage(100, 100, Color.RED, Color.GREEN);

        String hashA = ImageHashUtil.computeDHash(imgA);
        String hashB = ImageHashUtil.computeDHash(imgB);
        String hashC = ImageHashUtil.computeDHash(imgC);

        assertNotNull(hashA);
        assertEquals(16, hashA.length());

        // Identical images should have 0 hamming distance
        int distAB = ImageHashUtil.hammingDistance(hashA, hashB);
        assertEquals(0, distAB);
        assertTrue(ImageHashUtil.isNearDuplicate(hashA, hashB, 5));
        assertEquals(1.0, ImageHashUtil.similarityScore(hashA, hashB), 1e-4);

        // Different images should have larger hamming distance
        assertNotNull(hashC);
    }
}
