package com.sentinelcv.drift;

import com.sentinelcv.util.StatisticalUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares reference and evaluation feature distributions using
 * Population Stability Index (PSI), Kolmogorov-Smirnov (KS) test, and Wasserstein distance.
 * Also evaluates semantic class distributions and handles edge cases such as empty, small, or invalid samples.
 */
@Component
public class DistributionComparator {

    public static class FeatureComparison {
        public String featureName;
        public double refAverage;
        public double evalAverage;
        public double percentChange;
        public double psi;
        public double ksStatistic;
        public double wassersteinDistance;
        public boolean driftDetected;
        public String severity; // CRITICAL, HIGH, MEDIUM, LOW, NONE, UNAVAILABLE
    }

    public static class ComparisonResult {
        public String referenceName;
        public String evaluationName;
        public int refSampleCount;
        public int evalSampleCount;
        public boolean checksAvailable = true;
        public Map<String, FeatureComparison> featureComparisons = new HashMap<>();
        public double overallDriftScore; // 0.0 to 1.0
        public boolean driftDetected;
        public String overallSeverity; // CRITICAL, HIGH, MEDIUM, LOW, NONE, UNAVAILABLE
        public List<String> summaryStatements = new ArrayList<>();
    }

    public ComparisonResult compare(FeatureStatistics.DatasetFeatureDistributions ref,
                                    FeatureStatistics.DatasetFeatureDistributions eval,
                                    double psiThreshold,
                                    double ksThreshold) {
        return compareWithClasses(ref, eval, null, null, psiThreshold, ksThreshold);
    }

    public ComparisonResult compareWithClasses(FeatureStatistics.DatasetFeatureDistributions ref,
                                               FeatureStatistics.DatasetFeatureDistributions eval,
                                               Map<String, Integer> refClasses,
                                               Map<String, Integer> evalClasses,
                                               double psiThreshold,
                                               double ksThreshold) {
        ComparisonResult result = new ComparisonResult();
        result.referenceName = ref != null ? ref.datasetName : "UnknownRef";
        result.evaluationName = eval != null ? eval.datasetName : "UnknownEval";
        result.refSampleCount = ref != null ? ref.totalImages : 0;
        result.evalSampleCount = eval != null ? eval.totalImages : 0;

        if (ref == null || eval == null ||
                ref.totalImages < StatisticalUtil.MIN_SAMPLE_SIZE ||
                eval.totalImages < StatisticalUtil.MIN_SAMPLE_SIZE) {
            result.checksAvailable = false;
            result.overallSeverity = "UNAVAILABLE";
            result.driftDetected = false;
            result.summaryStatements.add(String.format(
                    "Statistical drift analysis UNAVAILABLE: Insufficient sample size (%d ref, %d eval; minimum required is %d).",
                    result.refSampleCount, result.evalSampleCount, StatisticalUtil.MIN_SAMPLE_SIZE));
            return result;
        }

        // Compare key visual features
        compareFeature("Brightness", ref.brightnessValues, eval.brightnessValues, ref.avgBrightness, eval.avgBrightness, psiThreshold, ksThreshold, result);
        compareFeature("Contrast", ref.contrastValues, eval.contrastValues, ref.avgContrast, eval.avgContrast, psiThreshold, ksThreshold, result);
        compareFeature("Mean Intensity", ref.meanIntensityValues, eval.meanIntensityValues, ref.avgMeanIntensity, eval.avgMeanIntensity, psiThreshold, ksThreshold, result);
        compareFeature("Red Channel", ref.redMeans, eval.redMeans, ref.avgRed, eval.avgRed, psiThreshold, ksThreshold, result);
        compareFeature("Green Channel", ref.greenMeans, eval.greenMeans, ref.avgGreen, eval.avgGreen, psiThreshold, ksThreshold, result);
        compareFeature("Blue Channel", ref.blueMeans, eval.blueMeans, ref.avgBlue, eval.avgBlue, psiThreshold, ksThreshold, result);
        compareFeature("Aspect Ratio", ref.aspectRatios, eval.aspectRatios, ref.avgAspectRatio, eval.avgAspectRatio, psiThreshold, ksThreshold, result);

        // Compare class distributions if annotation frequencies are present
        if (refClasses != null && evalClasses != null && !refClasses.isEmpty() && !evalClasses.isEmpty()) {
            compareClassFrequencies(refClasses, evalClasses, psiThreshold, result);
        }

        // Calculate Overall Composite Drift Score
        double sumPsi = 0;
        double sumKs = 0;
        int driftingFeatureCount = 0;
        int criticalCount = 0;
        int highCount = 0;

        for (FeatureComparison fc : result.featureComparisons.values()) {
            sumPsi += fc.psi;
            sumKs += fc.ksStatistic;
            if (fc.driftDetected) {
                driftingFeatureCount++;
                if ("CRITICAL".equals(fc.severity)) criticalCount++;
                if ("HIGH".equals(fc.severity)) highCount++;
            }
        }

        int count = result.featureComparisons.size();
        double avgPsi = count > 0 ? sumPsi / count : 0.0;
        double avgKs = count > 0 ? sumKs / count : 0.0;

        // Composite drift score between 0.0 and 1.0
        result.overallDriftScore = Math.min(1.0, (avgPsi * 0.5) + (avgKs * 0.5));
        result.driftDetected = driftingFeatureCount > 0;

        if (criticalCount > 0 || result.overallDriftScore >= 0.35) {
            result.overallSeverity = "CRITICAL";
        } else if (highCount >= 2 || result.overallDriftScore >= 0.20) {
            result.overallSeverity = "HIGH";
        } else if (driftingFeatureCount >= 1 || result.overallDriftScore >= 0.10) {
            result.overallSeverity = "MEDIUM";
        } else if (result.overallDriftScore >= 0.05) {
            result.overallSeverity = "LOW";
        } else {
            result.overallSeverity = "NONE";
        }

        result.summaryStatements.add(String.format("Compared %d visual & semantic features across %d reference and %d evaluation samples.",
                count, ref.totalImages, eval.totalImages));
        result.summaryStatements.add(String.format("Identified %d drifting features with composite drift score %.3f (Severity: %s).",
                driftingFeatureCount, result.overallDriftScore, result.overallSeverity));

        return result;
    }

    private void compareFeature(String name, double[] refVals, double[] evalVals,
                                double refAvg, double evalAvg,
                                double psiThreshold, double ksThreshold,
                                ComparisonResult result) {
        FeatureComparison fc = new FeatureComparison();
        fc.featureName = name;
        fc.refAverage = refAvg;
        fc.evalAverage = evalAvg;
        fc.percentChange = refAvg != 0.0 ? ((evalAvg - refAvg) / refAvg) * 100.0 : 0.0;

        if (!StatisticalUtil.isSampleSufficient(refVals, StatisticalUtil.MIN_SAMPLE_SIZE) ||
            !StatisticalUtil.isSampleSufficient(evalVals, StatisticalUtil.MIN_SAMPLE_SIZE)) {
            fc.severity = "UNAVAILABLE";
            fc.driftDetected = false;
            result.featureComparisons.put(name, fc);
            return;
        }

        fc.psi = StatisticalUtil.calculatePSI(refVals, evalVals, 10);
        fc.ksStatistic = StatisticalUtil.kolmogorovSmirnovStatistic(refVals, evalVals);
        fc.wassersteinDistance = StatisticalUtil.wassersteinDistance(refVals, evalVals);

        boolean psiDrift = fc.psi >= psiThreshold;
        boolean ksDrift = fc.ksStatistic >= ksThreshold;
        fc.driftDetected = psiDrift || ksDrift;

        if (fc.psi >= 0.30 || fc.ksStatistic >= 0.40) {
            fc.severity = "CRITICAL";
        } else if (fc.psi >= 0.20 || fc.ksStatistic >= 0.25) {
            fc.severity = "HIGH";
        } else if (fc.psi >= 0.10 || fc.ksStatistic >= 0.15) {
            fc.severity = "MEDIUM";
        } else if (fc.driftDetected) {
            fc.severity = "LOW";
        } else {
            fc.severity = "NONE";
        }

        result.featureComparisons.put(name, fc);
    }

    private void compareClassFrequencies(Map<String, Integer> refClasses, Map<String, Integer> evalClasses,
                                        double psiThreshold, ComparisonResult result) {
        FeatureComparison fc = new FeatureComparison();
        fc.featureName = "Class Distribution";
        fc.refAverage = refClasses.size();
        fc.evalAverage = evalClasses.size();
        fc.percentChange = fc.refAverage != 0.0 ? ((fc.evalAverage - fc.refAverage) / fc.refAverage) * 100.0 : 0.0;

        fc.psi = StatisticalUtil.calculateCategoricalPSI(refClasses, evalClasses);
        fc.ksStatistic = Math.min(1.0, fc.psi / 2.0); // Normalized proxy
        fc.wassersteinDistance = fc.psi;

        fc.driftDetected = fc.psi >= psiThreshold;
        if (fc.psi >= 0.35) {
            fc.severity = "CRITICAL";
        } else if (fc.psi >= 0.20) {
            fc.severity = "HIGH";
        } else if (fc.driftDetected) {
            fc.severity = "MEDIUM";
        } else {
            fc.severity = "NONE";
        }

        result.featureComparisons.put("Class Distribution", fc);
    }
}
