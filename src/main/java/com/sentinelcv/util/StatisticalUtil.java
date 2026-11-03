package com.sentinelcv.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Statistical utility for distribution comparison and shift detection.
 * Implements Population Stability Index (PSI), Kolmogorov-Smirnov (KS) statistic,
 * Wasserstein (Earth Mover's) Distance, and Categorical Class PSI.
 * Handles invalid statistical inputs, NaNs, infinities, and small/empty sample edge cases.
 */
public final class StatisticalUtil {

    private static final double EPSILON = 1e-6; // Prevents division by zero or ln(0)
    public static final int MIN_SAMPLE_SIZE = 5;

    private StatisticalUtil() {}

    /**
     * Checks if a sample has sufficient finite numerical data for valid statistical inference.
     */
    public static boolean isSampleSufficient(double[] sample, int minSize) {
        if (sample == null || sample.length < minSize) {
            return false;
        }
        int validCount = 0;
        for (double v : sample) {
            if (Double.isFinite(v)) {
                validCount++;
            }
        }
        return validCount >= minSize;
    }

    /**
     * Filters out NaN and infinite values, returning clean array of finite doubles.
     */
    public static double[] cleanSample(double[] raw) {
        if (raw == null || raw.length == 0) {
            return new double[0];
        }
        return Arrays.stream(raw)
                .filter(Double::isFinite)
                .toArray();
    }

    /**
     * Computes Population Stability Index (PSI) between reference and evaluation samples.
     * Evaluates global range across both distributions to reliably detect constant-to-shifted shifts.
     */
    public static double calculatePSI(double[] reference, double[] evaluation, int numBins) {
        double[] ref = cleanSample(reference);
        double[] eval = cleanSample(evaluation);

        if (ref.length == 0 || eval.length == 0) {
            return 0.0;
        }

        double refMin = Arrays.stream(ref).min().getAsDouble();
        double refMax = Arrays.stream(ref).max().getAsDouble();
        double evalMin = Arrays.stream(eval).min().getAsDouble();
        double evalMax = Arrays.stream(eval).max().getAsDouble();

        double min = Math.min(refMin, evalMin);
        double max = Math.max(refMax, evalMax);

        if (Math.abs(max - min) < EPSILON) {
            return 0.0; // Identical constant distributions
        }

        int bins = Math.max(2, numBins);
        double binWidth = (max - min) / bins;
        double[] refCounts = new double[bins];
        double[] evalCounts = new double[bins];

        for (double v : ref) {
            int bin = (int) ((v - min) / binWidth);
            bin = Math.min(bins - 1, Math.max(0, bin));
            refCounts[bin]++;
        }

        for (double v : eval) {
            int bin = (int) ((v - min) / binWidth);
            bin = Math.min(bins - 1, Math.max(0, bin));
            evalCounts[bin]++;
        }

        double totalRef = ref.length;
        double totalEval = eval.length;
        double psi = 0.0;

        for (int i = 0; i < bins; i++) {
            double refProp = (refCounts[i] + EPSILON) / (totalRef + bins * EPSILON);
            double evalProp = (evalCounts[i] + EPSILON) / (totalEval + bins * EPSILON);

            psi += (evalProp - refProp) * Math.log(evalProp / refProp);
        }

        return Math.max(0.0, psi);
    }

    /**
     * Calculates the two-sample Kolmogorov-Smirnov (KS) statistic (D-statistic).
     * D = sup_x |F_ref(x) - F_eval(x)|
     */
    public static double kolmogorovSmirnovStatistic(double[] sample1, double[] sample2) {
        double[] s1 = cleanSample(sample1);
        double[] s2 = cleanSample(sample2);

        if (s1.length == 0 || s2.length == 0) {
            return 0.0;
        }

        Arrays.sort(s1);
        Arrays.sort(s2);

        int n1 = s1.length;
        int n2 = s2.length;
        int i1 = 0;
        int i2 = 0;
        double dMax = 0.0;

        while (i1 < n1 && i2 < n2) {
            double v1 = s1[i1];
            double v2 = s2[i2];
            double currentVal = Math.min(v1, v2);

            while (i1 < n1 && s1[i1] <= currentVal) {
                i1++;
            }
            while (i2 < n2 && s2[i2] <= currentVal) {
                i2++;
            }

            double cdf1 = (double) i1 / n1;
            double cdf2 = (double) i2 / n2;
            double diff = Math.abs(cdf1 - cdf2);
            if (diff > dMax) {
                dMax = diff;
            }
        }

        return dMax;
    }

    /**
     * Calculates the 1D Wasserstein Distance (Earth Mover's Distance) between two samples.
     */
    public static double wassersteinDistance(double[] sample1, double[] sample2) {
        double[] s1 = cleanSample(sample1);
        double[] s2 = cleanSample(sample2);

        if (s1.length == 0 || s2.length == 0) {
            return 0.0;
        }

        Arrays.sort(s1);
        Arrays.sort(s2);

        int n1 = s1.length;
        int n2 = s2.length;
        double sum = 0.0;

        int steps = 100;
        for (int i = 0; i < steps; i++) {
            double q = (double) i / steps;
            int idx1 = (int) (q * (n1 - 1));
            int idx2 = (int) (q * (n2 - 1));
            sum += Math.abs(s1[idx1] - s2[idx2]);
        }

        return sum / steps;
    }

    /**
     * Computes Population Stability Index (PSI) between categorical frequency maps
     * (e.g. annotation class distributions).
     */
    public static double calculateCategoricalPSI(Map<String, Integer> refCounts, Map<String, Integer> evalCounts) {
        if (refCounts == null || evalCounts == null || refCounts.isEmpty() || evalCounts.isEmpty()) {
            return 0.0;
        }

        Set<String> allCategories = new HashSet<>(refCounts.keySet());
        allCategories.addAll(evalCounts.keySet());

        double totalRef = refCounts.values().stream().mapToDouble(Integer::doubleValue).sum();
        double totalEval = evalCounts.values().stream().mapToDouble(Integer::doubleValue).sum();

        if (totalRef <= 0 || totalEval <= 0) {
            return 0.0;
        }

        int numCategories = allCategories.size();
        double psi = 0.0;

        for (String cat : allCategories) {
            double refCount = refCounts.getOrDefault(cat, 0);
            double evalCount = evalCounts.getOrDefault(cat, 0);

            double refProp = (refCount + EPSILON) / (totalRef + numCategories * EPSILON);
            double evalProp = (evalCount + EPSILON) / (totalEval + numCategories * EPSILON);

            psi += (evalProp - refProp) * Math.log(evalProp / refProp);
        }

        return Math.max(0.0, psi);
    }
}
