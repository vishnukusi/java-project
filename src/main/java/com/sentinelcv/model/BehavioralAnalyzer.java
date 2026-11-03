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
 * Conducts controlled behavioral baseline testing on supported models.
 * Verifies deterministic repeatability and generates output vector fingerprints.
 */
@Component
public class BehavioralAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(BehavioralAnalyzer.class);

    public static class BehavioralResult {
        public boolean inferenceExecuted = false;
        public boolean deterministic = true;
        public double maxOutputDeviation = 0.0;
        public float[] baselineOutput;
        public double outputNorm = 0.0;
        public int topClassIndex = -1;
        public float topClassConfidence = 0.0f;
        public String statusDescription;
        public List<SecurityFinding> findings = new ArrayList<>();
    }

    /**
     * Executes controlled baseline testing on the model using standard synthetic inputs.
     */
    public BehavioralResult analyzeBehavior(ModelLoader loader, File modelFile, ModelMetadata metadata, Long auditId) {
        BehavioralResult res = new BehavioralResult();

        if (!metadata.inferenceSupported || metadata.inputTensors.isEmpty()) {
            res.inferenceExecuted = false;
            res.statusDescription = "Runtime inference is UNAVAILABLE for this model; skipping behavioral execution tests.";
            res.findings.add(SecurityFinding.builder()
                    .findingId("FIND-MODEL-BEHAVIOR-UNAVAILABLE")
                    .module("MODEL")
                    .category("Behavioral Testing")
                    .description("Dynamic behavioral inference testing is UNAVAILABLE because the model runtime or operators are unsupported.")
                    .severity(FindingSeverity.INFO)
                    .confidence(1.0)
                    .evidence(metadata.loadErrorMessage != null ? metadata.loadErrorMessage : "Runtime unsupported")
                    .affectedAsset(modelFile.getName())
                    .recommendedAction("Verify model in an environment supporting its specific custom operators.")
                    .sourceAuditId(auditId)
                    .sourceAuditType("MODEL")
                    .build());
            return res;
        }

        try {
            ModelMetadata.TensorInfo inputInfo = metadata.inputTensors.get(0);
            long[] shape = resolveEffectiveShape(inputInfo.shape);
            int totalElements = computeTotalElements(shape);

            // Construct structured normalized test input respecting layout and dimensions
            float[] testInput = generateStructuredInput(shape, totalElements);

            // Test Repeatability: Run 3 times
            float[] out1 = loader.runInference(modelFile, testInput, shape);
            float[] out2 = loader.runInference(modelFile, testInput, shape);
            float[] out3 = loader.runInference(modelFile, testInput, shape);

            res.inferenceExecuted = true;
            res.baselineOutput = out1;

            // Check deterministic repeatability
            double dev12 = computeMaxAbsDiff(out1, out2);
            double dev13 = computeMaxAbsDiff(out1, out3);
            res.maxOutputDeviation = Math.max(dev12, dev13);

            if (res.maxOutputDeviation > 1e-4) {
                res.deterministic = false;
                res.findings.add(SecurityFinding.builder()
                        .findingId("FIND-MODEL-NON-DETERMINISTIC")
                        .module("MODEL")
                        .category("Inference Determinism")
                        .description(String.format("Model produced non-deterministic outputs on identical inputs (max deviation: %.6f).",
                                res.maxOutputDeviation))
                        .severity(FindingSeverity.HIGH)
                        .confidence(0.95)
                        .evidence(String.format("Max deviation between consecutive executions: %.6f", res.maxOutputDeviation))
                        .affectedAsset(modelFile.getName())
                        .recommendedAction("Check for unseeded dropout, batch-normalization in training mode, or stateful operators.")
                        .sourceAuditId(auditId)
                        .sourceAuditType("MODEL")
                        .build());
            }

            // Output Characteristics
            double sumSq = 0;
            int maxIdx = 0;
            float maxVal = out1.length > 0 ? out1[0] : 0;
            for (int i = 0; i < out1.length; i++) {
                sumSq += out1[i] * out1[i];
                if (out1[i] > maxVal) {
                    maxVal = out1[i];
                    maxIdx = i;
                }
            }
            res.outputNorm = Math.sqrt(sumSq);
            res.topClassIndex = maxIdx;
            res.topClassConfidence = maxVal;
            res.statusDescription = String.format("Controlled inference successful. Output dimension: %d, Top-1 class: %d (logit: %.2f), Output norm: %.2f.",
                    out1.length, maxIdx, maxVal, res.outputNorm);

            return res;

        } catch (Exception e) {
            logger.warn("Behavioral testing failed for {}: {}", modelFile.getName(), e.getMessage());
            res.inferenceExecuted = false;
            res.statusDescription = "Controlled inference execution failed: " + e.getMessage();
            res.findings.add(SecurityFinding.builder()
                    .findingId("FIND-MODEL-EXEC-FAILURE")
                    .module("MODEL")
                    .category("Inference Execution")
                    .description("Failed to execute baseline inference: " + e.getMessage())
                    .severity(FindingSeverity.MEDIUM)
                    .confidence(1.0)
                    .evidence("Exception: " + e.getClass().getSimpleName())
                    .affectedAsset(modelFile.getName())
                    .recommendedAction("Verify model input shapes and valid tensor configurations.")
                    .sourceAuditId(auditId)
                    .sourceAuditType("MODEL")
                    .build());
            return res;
        }
    }

    public static int computeTotalElements(long[] shape) {
        long total = 1;
        for (long dim : shape) {
            long effectiveDim = dim <= 0 ? 1 : dim;
            if (effectiveDim > 10_000_000 || total > (Integer.MAX_VALUE - 8) / effectiveDim) {
                throw new IllegalArgumentException("Tensor dimensions exceed safe allocation limit");
            }
            total *= effectiveDim;
        }
        return (int) total;
    }

    public static float[] generateStructuredInput(long[] shape, int totalElements) {
        float[] input = new float[totalElements];
        if (shape.length == 4) {
            // NCHW layout: [N, C, H, W]
            int c = (int) shape[1];
            int h = (int) shape[2];
            int w = (int) shape[3];
            int spatial = h * w;

            // Channel-specific mean offsets (ImageNet standard: R:0.485, G:0.456, B:0.406)
            float[] channelMeans = new float[]{0.485f, 0.456f, 0.406f};
            float[] channelStds = new float[]{0.229f, 0.224f, 0.225f};

            for (int ci = 0; ci < c; ci++) {
                float mean = ci < channelMeans.length ? channelMeans[ci] : 0.5f;
                float std = ci < channelStds.length ? channelStds[ci] : 0.25f;
                int channelOffset = ci * spatial;

                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        // Smooth gradient in range [0, 1]
                        float rawPixel = ((float) (x + y)) / (float) (Math.max(1, w + h));
                        // Normalize with channel mean and std
                        float normalized = (rawPixel - mean) / std;
                        int idx = channelOffset + (y * w + x);
                        if (idx < totalElements) {
                            input[idx] = normalized;
                        }
                    }
                }
            }
        } else {
            // Non-4D tensor: uniform bounded structured ramp
            for (int i = 0; i < totalElements; i++) {
                input[i] = (float) (i % 100) / 100.0f;
            }
        }
        return input;
    }

    public static long[] resolveEffectiveShape(long[] shape) {
        if (shape == null || shape.length == 0) {
            return new long[]{1, 3, 224, 224}; // Standard CV fallback
        }
        long[] eff = new long[shape.length];
        for (int i = 0; i < shape.length; i++) {
            eff[i] = shape[i] <= 0 ? 1 : shape[i]; // Replace dynamic batch or dim with 1
        }
        return eff;
    }

    private double computeMaxAbsDiff(float[] a, float[] b) {
        if (a.length != b.length) return 1.0;
        double max = 0.0;
        for (int i = 0; i < a.length; i++) {
            double d = Math.abs(a[i] - b[i]);
            if (d > max) max = d;
        }
        return max;
    }
}
