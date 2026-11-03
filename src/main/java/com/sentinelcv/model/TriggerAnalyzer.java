package com.sentinelcv.model;

import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.FindingSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Executes black-box perturbation analysis to detect abnormal behavioral sensitivity
 * or trigger-like patterns in computer vision models.
 *
 * IMPORTANT:
 * This is a heuristic behavioral assessment, NOT formal proof of backdoor presence or absence.
 */
@Component
public class TriggerAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(TriggerAnalyzer.class);

    public static class PerturbationTestResult {
        public String perturbationType;
        public String parameters;
        public int baselineTopClass;
        public int perturbedTopClass;
        public float baselineConfidence;
        public float perturbedConfidence;
        public double outputCosineSimilarity;
        public boolean classFlipped;
        public double sensitivityScore;
    }

    public static class TriggerAnalysisReport {
        public boolean executed = false;
        public List<PerturbationTestResult> tests = new ArrayList<>();
        public boolean unusualSensitivityDetected = false;
        public String statusDescription;
        public List<SecurityFinding> findings = new ArrayList<>();
    }

    public TriggerAnalysisReport analyzePerturbations(ModelLoader loader, File modelFile,
                                                      ModelMetadata metadata, BehavioralAnalyzer.BehavioralResult baseline,
                                                      Long auditId) {
        TriggerAnalysisReport report = new TriggerAnalysisReport();

        if (!baseline.inferenceExecuted || baseline.baselineOutput == null || metadata.inputTensors.isEmpty()) {
            report.executed = false;
            report.statusDescription = "Perturbation analysis skipped because baseline inference is UNAVAILABLE.";
            return report;
        }

        try {
            ModelMetadata.TensorInfo inputInfo = metadata.inputTensors.get(0);
            long[] shape = resolveEffectiveShape(inputInfo.shape);
            int totalElements = BehavioralAnalyzer.computeTotalElements(shape);

            // Baseline clean input matching structured behavioral baseline
            float[] cleanInput = BehavioralAnalyzer.generateStructuredInput(shape, totalElements);

            // Test 1: Localized Spatial Patch (Watermark / Corner trigger pattern)
            PerturbationTestResult patchTest = runPatchTest(loader, modelFile, cleanInput, shape, baseline.baselineOutput);
            report.tests.add(patchTest);

            // Test 2: Global Noise Perturbation (Gaussian / uniform slight jitter)
            PerturbationTestResult noiseTest = runNoiseTest(loader, modelFile, cleanInput, shape, baseline.baselineOutput);
            report.tests.add(noiseTest);

            report.executed = true;

            // Analyze findings
            int flippedCount = 0;
            for (PerturbationTestResult test : report.tests) {
                if (test.classFlipped && test.outputCosineSimilarity < 0.70) {
                    flippedCount++;
                }
            }

            if (flippedCount > 0) {
                report.unusualSensitivityDetected = true;
                report.findings.add(SecurityFinding.builder()
                        .findingId("FIND-TRIGGER-SENSITIVITY-1")
                        .module("MODEL")
                        .category("Behavioral Perturbation Analysis")
                        .description(String.format("Unusual behavioral sensitivity: model flipped top-1 prediction under localized perturbation (Cosine similarity: %.3f).",
                                report.tests.get(0).outputCosineSimilarity))
                        .severity(FindingSeverity.MEDIUM)
                        .confidence(0.80)
                        .evidence(String.format("Patch perturbation: class flipped from %d (%.2f) to %d (%.2f)",
                                patchTest.baselineTopClass, patchTest.baselineConfidence,
                                patchTest.perturbedTopClass, patchTest.perturbedConfidence))
                        .affectedAsset(modelFile.getName())
                        .recommendedAction("Conduct targeted adversarial testing and inspect training data provenance for potential poisoned samples.")
                        .sourceAuditId(auditId)
                        .sourceAuditType("MODEL")
                        .build());
            }

            // Always add explicit heuristic notice (INFO)
            report.findings.add(SecurityFinding.builder()
                    .findingId("FIND-TRIGGER-DISCLAIMER")
                    .module("MODEL")
                    .category("Methodology Disclaimer")
                    .description("Perturbation testing is an empirical heuristic behavioral assessment, NOT formal proof of backdoor absence or presence.")
                    .severity(FindingSeverity.INFO)
                    .confidence(1.0)
                    .evidence("Evaluated 2 perturbation configurations (localized patch & global jitter)")
                    .affectedAsset(modelFile.getName())
                    .recommendedAction("Interpret perturbation findings in conjunction with dataset integrity and supply-chain provenance audits.")
                    .sourceAuditId(auditId)
                    .sourceAuditType("MODEL")
                    .build());

            report.statusDescription = String.format("Completed %d perturbation tests. Unusual sensitivity detected: %b.",
                    report.tests.size(), report.unusualSensitivityDetected);
            return report;

        } catch (Exception e) {
            logger.warn("Perturbation analysis failed for {}: {}", modelFile.getName(), e.getMessage());
            report.executed = false;
            report.statusDescription = "Perturbation analysis error: " + e.getMessage();
            return report;
        }
    }

    private PerturbationTestResult runPatchTest(ModelLoader loader, File modelFile, float[] cleanInput,
                                               long[] shape, float[] baselineOutput) {
        PerturbationTestResult res = new PerturbationTestResult();
        res.perturbationType = "Localized High-Contrast Patch";
        res.parameters = "Top-left 16x16 patch, pixel value = 1.0 (watermark trigger pattern)";

        float[] perturbed = Arrays.copyOf(cleanInput, cleanInput.length);
        if (shape.length == 4) {
            // True multi-channel spatial patch in NCHW layout: [N, C, H, W]
            int c = (int) shape[1];
            int h = (int) shape[2];
            int w = (int) shape[3];
            int spatial = h * w;
            int patchH = Math.min(16, h);
            int patchW = Math.min(16, w);

            for (int ci = 0; ci < c; ci++) {
                int channelOffset = ci * spatial;
                for (int y = 0; y < patchH; y++) {
                    for (int x = 0; x < patchW; x++) {
                        int idx = channelOffset + (y * w + x);
                        if (idx < perturbed.length) {
                            perturbed[idx] = 1.0f;
                        }
                    }
                }
            }
            res.parameters = String.format("Spatial %dx%d patch at top-left corner across %d channels, value = 1.0",
                    patchH, patchW, c);
        } else {
            int patchLen = Math.min(16, perturbed.length);
            for (int i = 0; i < patchLen; i++) {
                perturbed[i] = 1.0f;
            }
            res.parameters = "Localized vector patch on first " + patchLen + " dimensions";
        }

        float[] output = loader.runInference(modelFile, perturbed, shape);
        evaluatePredictionChange(baselineOutput, output, res);
        return res;
    }

    private PerturbationTestResult runNoiseTest(ModelLoader loader, File modelFile, float[] cleanInput,
                                               long[] shape, float[] baselineOutput) {
        PerturbationTestResult res = new PerturbationTestResult();
        res.perturbationType = "Subtle Uniform Noise";
        res.parameters = "Delta = +0.05 uniform intensity shift across all pixels";

        float[] perturbed = Arrays.copyOf(cleanInput, cleanInput.length);
        for (int i = 0; i < perturbed.length; i++) {
            perturbed[i] = Math.min(1.0f, perturbed[i] + 0.05f);
        }

        float[] output = loader.runInference(modelFile, perturbed, shape);
        evaluatePredictionChange(baselineOutput, output, res);
        return res;
    }

    private void evaluatePredictionChange(float[] base, float[] pert, PerturbationTestResult res) {
        int baseTop = getArgMax(base);
        int pertTop = getArgMax(pert);

        res.baselineTopClass = baseTop;
        res.perturbedTopClass = pertTop;
        res.baselineConfidence = base.length > 0 ? base[baseTop] : 0;
        res.perturbedConfidence = pert.length > 0 ? pert[pertTop] : 0;
        res.classFlipped = (baseTop != pertTop);
        res.outputCosineSimilarity = computeCosineSimilarity(base, pert);
        res.sensitivityScore = 1.0 - res.outputCosineSimilarity;
    }

    private int getArgMax(float[] arr) {
        if (arr.length == 0) return -1;
        int maxIdx = 0;
        float maxVal = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > maxVal) {
                maxVal = arr[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    private double computeCosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom > 1e-8 ? dot / denom : 0.0;
    }

    private long[] resolveEffectiveShape(long[] shape) {
        if (shape == null || shape.length == 0) return new long[]{1, 3, 224, 224};
        long[] eff = new long[shape.length];
        for (int i = 0; i < shape.length; i++) eff[i] = shape[i] <= 0 ? 1 : shape[i];
        return eff;
    }
}
