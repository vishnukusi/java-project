package com.sentinelcv.dataset;

import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.FindingSeverity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates computer vision annotations for bounding box integrity,
 * out-of-bounds coordinates, zero-area boxes, and invalid class mappings.
 */
@Component
public class AnnotationValidator {

    public static class ValidationResult {
        public int totalAnnotationsChecked = 0;
        public int zeroAreaCount = 0;
        public int outOfBoundsCount = 0;
        public int invalidClassIdCount = 0;
        public int emptyAnnotationImageCount = 0;
        public List<SecurityFinding> findings = new ArrayList<>();
    }

    public ValidationResult validate(DatasetModels.ParseResult parseResult, Long auditId) {
        ValidationResult result = new ValidationResult();
        Map<Integer, String> validCategories = parseResult.categories;

        int findingCounter = 1;

        // Check for images without any annotations
        for (DatasetModels.ImageInfo img : parseResult.images) {
            if (img.annotations.isEmpty()) {
                result.emptyAnnotationImageCount++;
            }
        }

        if (result.emptyAnnotationImageCount > 0) {
            result.findings.add(SecurityFinding.builder()
                    .findingId("FIND-ANN-EMPTY-" + findingCounter++)
                    .module("DATASET")
                    .category("Annotation Integrity")
                    .description(String.format("%d images have no annotations (empty labels)", result.emptyAnnotationImageCount))
                    .severity(FindingSeverity.LOW)
                    .confidence(1.0)
                    .evidence("Unlabeled image count: " + result.emptyAnnotationImageCount)
                    .affectedAsset("Dataset Annotations")
                    .recommendedAction("Verify whether unannotated images are intentional negative samples or missing labels.")
                    .sourceAuditId(auditId)
                    .sourceAuditType("DATASET")
                    .build());
        }

        // Validate each annotation
        for (DatasetModels.AnnotationInfo ann : parseResult.annotations) {
            result.totalAnnotationsChecked++;

            // 1. Check Class ID validity
            if (ann.categoryId < 0 || (!validCategories.isEmpty() && !validCategories.containsKey(ann.categoryId))) {
                result.invalidClassIdCount++;
                if (result.invalidClassIdCount <= 10) { // Limit individual findings
                    result.findings.add(SecurityFinding.builder()
                            .findingId("FIND-ANN-INVALID-CLASS-" + findingCounter++)
                            .module("DATASET")
                            .category("Annotation Integrity")
                            .description(String.format("Annotation %s references unknown class ID %d", ann.id, ann.categoryId))
                            .severity(FindingSeverity.HIGH)
                            .confidence(1.0)
                            .evidence(String.format("Image: %s, CategoryId: %d", ann.imageId, ann.categoryId))
                            .affectedAsset("Annotation " + ann.id)
                            .recommendedAction("Fix or remap the annotation to a recognized category.")
                            .sourceAuditId(auditId)
                            .sourceAuditType("DATASET")
                            .build());
                }
            }

            if (ann.bbox == null) {
                continue;
            }

            // 2. Check Zero/Negative Area
            if (ann.bbox.isZeroArea()) {
                result.zeroAreaCount++;
                if (result.zeroAreaCount <= 10) {
                    result.findings.add(SecurityFinding.builder()
                            .findingId("FIND-ANN-ZERO-AREA-" + findingCounter++)
                            .module("DATASET")
                            .category("Annotation Integrity")
                            .description(String.format("Annotation %s has zero or negative bounding box area (w=%.2f, h=%.2f)",
                                    ann.id, ann.bbox.width, ann.bbox.height))
                            .severity(FindingSeverity.HIGH)
                            .confidence(1.0)
                            .evidence(String.format("Dimensions: width=%.3f, height=%.3f", ann.bbox.width, ann.bbox.height))
                            .affectedAsset("Annotation " + ann.id)
                            .recommendedAction("Remove or reconstruct corrupted zero-area annotations.")
                            .sourceAuditId(auditId)
                            .sourceAuditType("DATASET")
                            .build());
                }
            }

            // 3. Check Out-of-Bounds Coordinates
            // For YOLO (normalized), check bounds directly (1x1 virtual dimension)
            // For COCO, resolve image dimension
            int imgW = 1000;
            int imgH = 1000;
            if (!ann.bbox.isNormalized) {
                // Find image width and height
                for (DatasetModels.ImageInfo img : parseResult.images) {
                    if (img.id.equals(ann.imageId)) {
                        imgW = img.width > 0 ? img.width : 1000;
                        imgH = img.height > 0 ? img.height : 1000;
                        break;
                    }
                }
            }

            if (ann.bbox.isOutOfBounds(imgW, imgH)) {
                result.outOfBoundsCount++;
                if (result.outOfBoundsCount <= 10) {
                    result.findings.add(SecurityFinding.builder()
                            .findingId("FIND-ANN-OOB-" + findingCounter++)
                            .module("DATASET")
                            .category("Annotation Integrity")
                            .description(String.format("Annotation %s coordinates exceed image boundaries (x=%.2f, y=%.2f, w=%.2f, h=%.2f)",
                                    ann.id, ann.bbox.x, ann.bbox.y, ann.bbox.width, ann.bbox.height))
                            .severity(FindingSeverity.HIGH)
                            .confidence(0.95)
                            .evidence(String.format("Coords: (%.2f, %.2f, %.2f, %.2f) in boundary (%d, %d)",
                                    ann.bbox.x, ann.bbox.y, ann.bbox.width, ann.bbox.height, imgW, imgH))
                            .affectedAsset("Annotation " + ann.id)
                            .recommendedAction("Clip bounding box coordinates to image boundaries.")
                            .sourceAuditId(auditId)
                            .sourceAuditType("DATASET")
                            .build());
                }
            }
        }

        return result;
    }
}
