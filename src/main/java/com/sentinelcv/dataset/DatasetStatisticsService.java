package com.sentinelcv.dataset;

import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.FindingSeverity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes dataset-level statistics, class distribution analysis,
 * contributor risk assessment, and overall integrity score calculation.
 */
@Component
public class DatasetStatisticsService {

    public static class DatasetStats {
        public int totalImages;
        public int totalAnnotations;
        public int totalClasses;
        public Map<String, Integer> classDistribution = new HashMap<>();
        public double avgAnnotationsPerImage;
        public double classImbalanceRatio; // max / min count
        public String dominantClass;
        public String rarestClass;
        public double integrityScore; // 0.0 to 100.0
        public List<SecurityFinding> findings = new ArrayList<>();
    }

    public DatasetStats calculateStatistics(DatasetModels.ParseResult parseResult,
                                            List<SecurityFinding> existingFindings,
                                            Long auditId) {
        DatasetStats stats = new DatasetStats();
        stats.totalImages = parseResult.images.size();
        stats.totalAnnotations = parseResult.annotations.size();
        stats.totalClasses = parseResult.categories.size();

        // 1. Calculate Class Distribution
        for (DatasetModels.AnnotationInfo ann : parseResult.annotations) {
            String catName = ann.categoryName != null ? ann.categoryName : ("Class_" + ann.categoryId);
            stats.classDistribution.merge(catName, 1, Integer::sum);
        }

        if (stats.totalImages > 0) {
            stats.avgAnnotationsPerImage = (double) stats.totalAnnotations / stats.totalImages;
        }

        // 2. Class Imbalance Analysis
        if (!stats.classDistribution.isEmpty()) {
            int maxCount = Collections.max(stats.classDistribution.values());
            int minCount = Collections.min(stats.classDistribution.values());
            stats.classImbalanceRatio = minCount > 0 ? (double) maxCount / minCount : maxCount;

            for (Map.Entry<String, Integer> entry : stats.classDistribution.entrySet()) {
                if (entry.getValue() == maxCount) stats.dominantClass = entry.getKey();
                if (entry.getValue() == minCount) stats.rarestClass = entry.getKey();
            }

            if (stats.classImbalanceRatio > 20.0 && stats.totalClasses > 1) {
                stats.findings.add(SecurityFinding.builder()
                        .findingId("FIND-DATASET-IMBALANCE-1")
                        .module("DATASET")
                        .category("Class Distribution")
                        .description(String.format("Severe class imbalance detected: imbalance ratio is %.1f:1 (Dominant: %s with %d, Rarest: %s with %d)",
                                stats.classImbalanceRatio, stats.dominantClass, maxCount, stats.rarestClass, minCount))
                        .severity(FindingSeverity.MEDIUM)
                        .confidence(0.95)
                        .evidence(String.format("Max class: %s (%d), Min class: %s (%d)", stats.dominantClass, maxCount, stats.rarestClass, minCount))
                        .affectedAsset("Class Distribution")
                        .recommendedAction("Apply data augmentation, class weighting, or targeted data collection to balance classes.")
                        .sourceAuditId(auditId)
                        .sourceAuditType("DATASET")
                        .build());
            }
        }

        // 3. Contributor Risk Analysis
        if (parseResult.contributorMetadata != null && parseResult.contributorMetadata.present) {
            Map<String, Integer> contribCounts = parseResult.contributorMetadata.sampleCountsByContributor;
            if (!contribCounts.isEmpty()) {
                int totalContribSamples = contribCounts.values().stream().mapToInt(Integer::intValue).sum();
                for (Map.Entry<String, Integer> entry : contribCounts.entrySet()) {
                    double share = (double) entry.getValue() / totalContribSamples;
                    if (share > 0.70 && contribCounts.size() > 1) {
                        stats.findings.add(SecurityFinding.builder()
                                .findingId("FIND-CONTRIB-CONCENTRATION-1")
                                .module("DATASET")
                                .category("Contributor Risk")
                                .description(String.format("High contributor concentration: '%s' supplied %.1f%% of all samples (%d of %d)",
                                        entry.getKey(), share * 100.0, entry.getValue(), totalContribSamples))
                                .severity(FindingSeverity.LOW)
                                .confidence(0.85)
                                .evidence("Contributor: " + entry.getKey() + ", Samples: " + entry.getValue())
                                .affectedAsset("Contributor Provenance")
                                .recommendedAction("Diversify data collection sources to mitigate bias or targeted manipulation.")
                                .sourceAuditId(auditId)
                                .sourceAuditType("DATASET")
                                .build());
                    }
                }
            }
        } else {
            // Contributor metadata absent -> honest UNAVAILABLE finding (INFO level)
            stats.findings.add(SecurityFinding.builder()
                    .findingId("FIND-CONTRIB-UNAVAILABLE-1")
                    .module("DATASET")
                    .category("Contributor Risk")
                    .description("Contributor provenance metadata is not present in this dataset; contributor risk check is UNAVAILABLE.")
                    .severity(FindingSeverity.INFO)
                    .confidence(1.0)
                    .evidence("Dataset metadata contains no author/contributor fields")
                    .affectedAsset("Dataset Provenance")
                    .recommendedAction("Incorporate contributor signatures or provenance metadata in future data collection.")
                    .sourceAuditId(auditId)
                    .sourceAuditType("DATASET")
                    .build());
        }

        // 4. Calculate Overall Integrity Score (0.0 to 100.0)
        List<SecurityFinding> allFindings = new ArrayList<>(existingFindings);
        allFindings.addAll(stats.findings);

        double score = 100.0;
        for (SecurityFinding f : allFindings) {
            switch (f.getSeverity()) {
                case CRITICAL -> score -= 25.0;
                case HIGH -> score -= 10.0;
                case MEDIUM -> score -= 4.0;
                case LOW -> score -= 1.0;
                case INFO -> {} // No score penalty for informative/unavailable notices
            }
        }
        stats.integrityScore = Math.max(0.0, Math.min(100.0, score));

        return stats;
    }
}
