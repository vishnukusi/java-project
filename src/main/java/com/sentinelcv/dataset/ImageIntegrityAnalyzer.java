package com.sentinelcv.dataset;

import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.FindingSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes file and image-level integrity across dataset images.
 * Identifies corrupt files, unreadable headers, and dimension inconsistencies.
 */
@Component
public class ImageIntegrityAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(ImageIntegrityAnalyzer.class);

    public static class ImageIntegrityResult {
        public int corruptImageCount = 0;
        public int zeroByteCount = 0;
        public int extremeAspectCount = 0;
        public List<SecurityFinding> findings = new ArrayList<>();
    }

    public ImageIntegrityResult analyze(File datasetDir, List<DatasetModels.ImageInfo> images, Long auditId) {
        ImageIntegrityResult result = new ImageIntegrityResult();
        int findingCounter = 1;

        for (DatasetModels.ImageInfo info : images) {
            File imgFile = new File(datasetDir, info.relativePath);
            if (!imgFile.exists()) {
                result.corruptImageCount++;
                result.findings.add(SecurityFinding.builder()
                        .findingId("FIND-IMG-MISSING-" + findingCounter++)
                        .module("DATASET")
                        .category("Image Integrity")
                        .description("Referenced image file is missing from dataset storage: " + info.fileName)
                        .severity(FindingSeverity.CRITICAL)
                        .confidence(1.0)
                        .evidence("Relative path: " + info.relativePath)
                        .affectedAsset(info.fileName)
                        .recommendedAction("Ensure all referenced image files are bundled in the dataset archive.")
                        .sourceAuditId(auditId)
                        .sourceAuditType("DATASET")
                        .build());
                continue;
            }

            if (imgFile.length() == 0) {
                result.zeroByteCount++;
                result.findings.add(SecurityFinding.builder()
                        .findingId("FIND-IMG-ZERO-BYTE-" + findingCounter++)
                        .module("DATASET")
                        .category("Image Integrity")
                        .description("Zero-byte empty image file detected: " + info.fileName)
                        .severity(FindingSeverity.CRITICAL)
                        .confidence(1.0)
                        .evidence("File size: 0 bytes")
                        .affectedAsset(info.fileName)
                        .recommendedAction("Replace or remove empty image file.")
                        .sourceAuditId(auditId)
                        .sourceAuditType("DATASET")
                        .build());
                continue;
            }

            // Test decoding image
            try {
                BufferedImage bimg = ImageIO.read(imgFile);
                if (bimg == null) {
                    result.corruptImageCount++;
                    result.findings.add(SecurityFinding.builder()
                            .findingId("FIND-IMG-CORRUPT-" + findingCounter++)
                            .module("DATASET")
                            .category("Image Integrity")
                            .description("Failed to decode image data; file may be corrupted or in unsupported format: " + info.fileName)
                            .severity(FindingSeverity.HIGH)
                            .confidence(1.0)
                            .evidence("Path: " + info.relativePath + ", size: " + imgFile.length() + " bytes")
                            .affectedAsset(info.fileName)
                            .recommendedAction("Re-encode or remove the corrupted image file.")
                            .sourceAuditId(auditId)
                            .sourceAuditType("DATASET")
                            .build());
                } else {
                    int w = bimg.getWidth();
                    int h = bimg.getHeight();
                    double aspect = (double) w / h;
                    if (aspect < 0.1 || aspect > 10.0) {
                        result.extremeAspectCount++;
                        result.findings.add(SecurityFinding.builder()
                                .findingId("FIND-IMG-EXTREME-ASPECT-" + findingCounter++)
                                .module("DATASET")
                                .category("Image Integrity")
                                .description(String.format("Extreme image aspect ratio detected (%dx%d, aspect ratio %.2f): %s",
                                        w, h, aspect, info.fileName))
                                .severity(FindingSeverity.LOW)
                                .confidence(0.95)
                                .evidence(String.format("Width=%d, Height=%d, Aspect=%.2f", w, h, aspect))
                                .affectedAsset(info.fileName)
                                .recommendedAction("Verify if unusual aspect ratio is intended or represents a preprocessing distortion.")
                                .sourceAuditId(auditId)
                                .sourceAuditType("DATASET")
                                .build());
                    }
                }
            } catch (Exception e) {
                result.corruptImageCount++;
                result.findings.add(SecurityFinding.builder()
                        .findingId("FIND-IMG-DECODE-ERROR-" + findingCounter++)
                        .module("DATASET")
                        .category("Image Integrity")
                        .description("Exception encountered while decoding image " + info.fileName + ": " + e.getMessage())
                        .severity(FindingSeverity.HIGH)
                        .confidence(1.0)
                        .evidence("Exception: " + e.getClass().getSimpleName())
                        .affectedAsset(info.fileName)
                        .recommendedAction("Inspect image file integrity.")
                        .sourceAuditId(auditId)
                        .sourceAuditType("DATASET")
                        .build());
            }
        }

        return result;
    }
}
