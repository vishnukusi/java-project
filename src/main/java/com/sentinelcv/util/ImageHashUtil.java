package com.sentinelcv.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Utility for computing perceptual hashes (dHash - Difference Hash and aHash - Average Hash)
 * of images, and calculating Hamming distance / similarity score between hashes.
 * Used for near-duplicate image detection.
 */
public final class ImageHashUtil {

    private ImageHashUtil() {}

    /**
     * Computes the Difference Hash (dHash) of an image file.
     * Resizes to 9x8, converts to grayscale, and compares adjacent pixels horizontally.
     * Returns a 64-bit hash encoded as a 16-character hexadecimal string.
     */
    public static String computeDHash(File imageFile) throws IOException {
        BufferedImage img = ImageIO.read(imageFile);
        if (img == null) {
            throw new IOException("Failed to decode image from file: " + imageFile.getName());
        }
        return computeDHash(img);
    }

    /**
     * Computes dHash from an InputStream.
     */
    public static String computeDHash(InputStream is) throws IOException {
        BufferedImage img = ImageIO.read(is);
        if (img == null) {
            throw new IOException("Failed to decode image from stream");
        }
        return computeDHash(img);
    }

    /**
     * Computes dHash from a BufferedImage.
     */
    public static String computeDHash(BufferedImage image) {
        // Resize to 9x8 grayscale
        BufferedImage resized = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.drawImage(image.getScaledInstance(9, 8, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();

        long hash = 0L;
        int bitIndex = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int left = resized.getRaster().getSample(x, y, 0);
                int right = resized.getRaster().getSample(x + 1, y, 0);
                if (left > right) {
                    hash |= (1L << bitIndex);
                }
                bitIndex++;
            }
        }
        return String.format("%016x", hash);
    }

    /**
     * Computes the Average Hash (aHash) of an image.
     * Resizes to 8x8, computes mean pixel brightness, and sets bits for pixels above average.
     */
    public static String computeAHash(BufferedImage image) {
        BufferedImage resized = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = resized.createGraphics();
        g.drawImage(image.getScaledInstance(8, 8, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();

        long sum = 0;
        int[] pixels = new int[64];
        int idx = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int val = resized.getRaster().getSample(x, y, 0);
                pixels[idx++] = val;
                sum += val;
            }
        }
        double avg = (double) sum / 64.0;

        long hash = 0L;
        for (int i = 0; i < 64; i++) {
            if (pixels[i] >= avg) {
                hash |= (1L << i);
            }
        }
        return String.format("%016x", hash);
    }

    /**
     * Calculates the Hamming distance between two 16-character hexadecimal perceptual hashes.
     * Range: 0 (identical) to 64 (completely opposite).
     */
    public static int hammingDistance(String hash1, String hash2) {
        if (hash1 == null || hash2 == null || hash1.length() != 16 || hash2.length() != 16) {
            throw new IllegalArgumentException("Hashes must be 16-character hex strings");
        }
        long val1 = Long.parseUnsignedLong(hash1, 16);
        long val2 = Long.parseUnsignedLong(hash2, 16);
        return Long.bitCount(val1 ^ val2);
    }

    /**
     * Calculates normalized similarity score between 0.0 (completely dissimilar) and 1.0 (identical).
     */
    public static double similarityScore(String hash1, String hash2) {
        int distance = hammingDistance(hash1, hash2);
        return 1.0 - ((double) distance / 64.0);
    }

    /**
     * Determines whether two hashes represent near-duplicates given a maximum Hamming distance threshold.
     * Standard threshold is typically <= 10.
     */
    public static boolean isNearDuplicate(String hash1, String hash2, int threshold) {
        return hammingDistance(hash1, hash2) <= threshold;
    }
}
