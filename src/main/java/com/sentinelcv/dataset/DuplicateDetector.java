package com.sentinelcv.dataset;

import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.FindingSeverity;
import com.sentinelcv.util.HashUtil;
import com.sentinelcv.util.ImageHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects exact duplicates (via SHA-256) and near-duplicates (via perceptual dHash).
 * Groups duplicate images and produces evidence-backed security findings.
 */
@Component
public class DuplicateDetector {

    private static final Logger logger = LoggerFactory.getLogger(DuplicateDetector.class);

    public static class DuplicateResult {
        public List<List<String>> exactDuplicateGroups = new ArrayList<>();
        public List<List<String>> nearDuplicateGroups = new ArrayList<>();
        public int totalExactDuplicates = 0;
        public int totalNearDuplicates = 0;
        public List<SecurityFinding> findings = new ArrayList<>();
    }

    /**
     * Performs exact and near-duplicate analysis across all images in a dataset.
     */
    public DuplicateResult detectDuplicates(File datasetDir, List<DatasetModels.ImageInfo> images,
                                           int hammingThreshold, Long auditId) {
        DuplicateResult result = new DuplicateResult();

        // 1. Calculate SHA-256 and dHash for all images
        Map<String, List<DatasetModels.ImageInfo>> sha256Map = new HashMap<>();
        List<DatasetModels.ImageInfo> processedImages = new ArrayList<>();

        for (DatasetModels.ImageInfo img : images) {
            File imgFile = new File(datasetDir, img.relativePath);
            if (!imgFile.exists() || !imgFile.isFile()) {
                continue;
            }

            try {
                img.sha256 = HashUtil.sha256(imgFile);
                img.dHash = ImageHashUtil.computeDHash(imgFile);
                sha256Map.computeIfAbsent(img.sha256, k -> new ArrayList<>()).add(img);
                processedImages.add(img);
            } catch (IOException e) {
                logger.warn("Failed to compute hash for image: {}", img.fileName);
            }
        }

        // 2. Identify Exact Duplicates (SHA-256 collision)
        Set<String> exactDuplicateFiles = new HashSet<>();
        int exactGroupIndex = 1;
        for (Map.Entry<String, List<DatasetModels.ImageInfo>> entry : sha256Map.entrySet()) {
            if (entry.getValue().size() > 1) {
                List<String> group = new ArrayList<>();
                for (DatasetModels.ImageInfo info : entry.getValue()) {
                    group.add(info.fileName);
                    exactDuplicateFiles.add(info.fileName);
                }
                result.exactDuplicateGroups.add(group);
                result.totalExactDuplicates += (group.size() - 1);

                result.findings.add(SecurityFinding.builder()
                        .findingId("FIND-DATASET-EXACT-DUP-" + exactGroupIndex)
                        .module("DATASET")
                        .category("Duplicate Detection")
                        .description(String.format("Found %d exact duplicate images sharing SHA-256 hash (%s)",
                                group.size(), entry.getKey().substring(0, 16) + "..."))
                        .severity(FindingSeverity.HIGH)
                        .confidence(1.0)
                        .evidence("Duplicate group: " + String.join(", ", group))
                        .affectedAsset(group.get(0))
                        .recommendedAction("Remove redundant identical image copies to prevent model overfitting.")
                        .sourceAuditId(auditId)
                        .sourceAuditType("DATASET")
                        .build());
                exactGroupIndex++;
            }
        }

        // 3. Identify Near-Duplicates using Perceptual Hash (dHash)
        int nearGroupIndex = 1;
        Set<Integer> visitedIndices = new HashSet<>();
        int n = processedImages.size();

        for (int i = 0; i < n; i++) {
            if (visitedIndices.contains(i)) continue;
            DatasetModels.ImageInfo imgA = processedImages.get(i);
            if (imgA.dHash == null) continue;

            List<String> nearGroup = new ArrayList<>();

            for (int j = i + 1; j < n; j++) {
                if (visitedIndices.contains(j)) continue;
                DatasetModels.ImageInfo imgB = processedImages.get(j);
                if (imgB.dHash == null) continue;

                // Skip if already flagged as exact duplicate of imgA
                if (imgA.sha256 != null && imgA.sha256.equals(imgB.sha256)) continue;

                int dist = ImageHashUtil.hammingDistance(imgA.dHash, imgB.dHash);
                if (dist <= hammingThreshold) {
                    if (nearGroup.isEmpty()) {
                        nearGroup.add(imgA.fileName);
                        visitedIndices.add(i);
                    }
                    nearGroup.add(imgB.fileName);
                    visitedIndices.add(j);
                }
            }

            if (!nearGroup.isEmpty()) {
                result.nearDuplicateGroups.add(nearGroup);
                result.totalNearDuplicates += (nearGroup.size() - 1);

                result.findings.add(SecurityFinding.builder()
                        .findingId("FIND-DATASET-NEAR-DUP-" + nearGroupIndex)
                        .module("DATASET")
                        .category("Near Duplicate Detection")
                        .description(String.format("Found %d visually similar near-duplicate images (Hamming distance <= %d)",
                                nearGroup.size(), hammingThreshold))
                        .severity(FindingSeverity.MEDIUM)
                        .confidence(0.90)
                        .evidence("Similar cluster: " + String.join(", ", nearGroup))
                        .affectedAsset(nearGroup.get(0))
                        .recommendedAction("Review near-duplicate samples for data leakage, re-sampling, or slight corruptions.")
                        .sourceAuditId(auditId)
                        .sourceAuditType("DATASET")
                        .build());
                nearGroupIndex++;
            }
        }

        return result;
    }
}
