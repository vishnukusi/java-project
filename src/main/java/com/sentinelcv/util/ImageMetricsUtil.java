package com.sentinelcv.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Utility for extracting visual and statistical metrics from images.
 * Computes mean intensity, standard deviation, brightness, contrast,
 * color channel statistics, and luminance histograms.
 */
public final class ImageMetricsUtil {

    private ImageMetricsUtil() {}

    public static class ImageStats {
        public int width;
        public int height;
        public double aspectRatio;
        public double meanIntensity;
        public double stdDevIntensity;
        public double brightness;
        public double contrast;
        public double redMean;
        public double greenMean;
        public double blueMean;
        public double redStdDev;
        public double greenStdDev;
        public double blueStdDev;
        public int[] luminanceHistogram = new int[256];

        @Override
        public String toString() {
            return String.format("ImageStats[%dx%d, Brightness=%.2f, Contrast=%.2f, MeanRGB=(%.1f, %.1f, %.1f)]",
                    width, height, brightness, contrast, redMean, greenMean, blueMean);
        }
    }

    /**
     * Extracts statistical metrics from an image file.
     */
    public static ImageStats extractStats(File imageFile) throws IOException {
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("Failed to decode image from: " + imageFile.getName());
        }
        return extractStats(image);
    }

    /**
     * Extracts statistical metrics from a BufferedImage.
     */
    public static ImageStats extractStats(BufferedImage image) {
        ImageStats stats = new ImageStats();
        int width = image.getWidth();
        int height = image.getHeight();
        stats.width = width;
        stats.height = height;
        stats.aspectRatio = (double) width / (double) height;

        long totalPixels = (long) width * height;
        if (totalPixels == 0) {
            return stats;
        }

        double rSum = 0, gSum = 0, bSum = 0, lumSum = 0;
        int[] rgbArray = image.getRGB(0, 0, width, height, null, 0, width);

        for (int rgb : rgbArray) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            rSum += r;
            gSum += g;
            bSum += b;

            // Perceived luminance (ITU-R BT.601)
            int lum = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
            lum = Math.min(255, Math.max(0, lum));
            stats.luminanceHistogram[lum]++;
            lumSum += lum;
        }

        stats.redMean = rSum / totalPixels;
        stats.greenMean = gSum / totalPixels;
        stats.blueMean = bSum / totalPixels;
        stats.meanIntensity = (stats.redMean + stats.greenMean + stats.blueMean) / 3.0;
        stats.brightness = lumSum / totalPixels;

        // Second pass: variance and standard deviation
        double rVarSum = 0, gVarSum = 0, bVarSum = 0, lumVarSum = 0;
        for (int rgb : rgbArray) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            int lum = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);

            rVarSum += Math.pow(r - stats.redMean, 2);
            gVarSum += Math.pow(g - stats.greenMean, 2);
            bVarSum += Math.pow(b - stats.blueMean, 2);
            lumVarSum += Math.pow(lum - stats.brightness, 2);
        }

        stats.redStdDev = Math.sqrt(rVarSum / totalPixels);
        stats.greenStdDev = Math.sqrt(gVarSum / totalPixels);
        stats.blueStdDev = Math.sqrt(bVarSum / totalPixels);
        stats.contrast = Math.sqrt(lumVarSum / totalPixels);
        stats.stdDevIntensity = (stats.redStdDev + stats.greenStdDev + stats.blueStdDev) / 3.0;

        return stats;
    }
}
