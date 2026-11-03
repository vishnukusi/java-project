package com.sentinelcv.drift;

import com.sentinelcv.util.StatisticalUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DriftStatisticalEdgeCasesTest {

    private final DistributionComparator comparator = new DistributionComparator();

    @Test
    void testIdenticalDistributionsProduceNoDrift() {
        double[] sample = new double[]{10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 70.0, 80.0};
        double psi = StatisticalUtil.calculatePSI(sample, sample, 10);
        double ks = StatisticalUtil.kolmogorovSmirnovStatistic(sample, sample);

        assertEquals(0.0, ks, 1e-6, "KS statistic for identical distributions must be zero");
        assertTrue(psi < 0.05, "PSI for identical distributions must be negligible");
    }

    @Test
    void testSignificantBrightnessShiftDetected() {
        double[] refBrightness = new double[]{20.0, 22.0, 25.0, 28.0, 30.0, 32.0, 35.0};
        double[] evalBrightness = new double[]{80.0, 82.0, 85.0, 88.0, 90.0, 92.0, 95.0}; // Severe brightness shift

        double psi = StatisticalUtil.calculatePSI(refBrightness, evalBrightness, 10);
        double ks = StatisticalUtil.kolmogorovSmirnovStatistic(refBrightness, evalBrightness);

        assertTrue(psi > 0.3, "Severe brightness shift must yield high PSI");
        assertTrue(ks > 0.8, "Disjoint distributions must yield high KS statistic");
    }

    @Test
    void testShiftFromConstantToAnotherConstant() {
        double[] ref = new double[]{0.5, 0.5, 0.5, 0.5, 0.5};
        double[] eval = new double[]{0.9, 0.9, 0.9, 0.9, 0.9};

        double psi = StatisticalUtil.calculatePSI(ref, eval, 5);
        double ks = StatisticalUtil.kolmogorovSmirnovStatistic(ref, eval);

        assertTrue(psi > 0.2, "Shift from constant 0.5 to constant 0.9 must be detected as drift");
        assertEquals(1.0, ks, 1e-4, "Disjoint constant values must yield maximum KS distance of 1.0");
    }

    @Test
    void testSmallSampleHandlingProducesUnavailable() {
        FeatureStatistics.DatasetFeatureDistributions ref = new FeatureStatistics.DatasetFeatureDistributions();
        ref.datasetName = "small_ref";
        ref.totalImages = 3; // < 5 minimum
        ref.brightnessValues = new double[]{10.0, 20.0, 30.0};

        FeatureStatistics.DatasetFeatureDistributions eval = new FeatureStatistics.DatasetFeatureDistributions();
        eval.datasetName = "small_eval";
        eval.totalImages = 3; // < 5 minimum
        eval.brightnessValues = new double[]{10.0, 20.0, 30.0};

        DistributionComparator.ComparisonResult result = comparator.compare(ref, eval, 0.2, 0.2);

        assertFalse(result.checksAvailable, "Small sample size must flag checks as unavailable");
        assertEquals("UNAVAILABLE", result.overallSeverity);
        assertFalse(result.driftDetected, "Must not claim false drift detection when checks are unavailable");
        assertTrue(result.summaryStatements.get(0).contains("UNAVAILABLE"));
    }

    @Test
    void testEmptyDatasetHandlingProducesUnavailable() {
        FeatureStatistics.DatasetFeatureDistributions ref = new FeatureStatistics.DatasetFeatureDistributions();
        ref.totalImages = 0;
        FeatureStatistics.DatasetFeatureDistributions eval = new FeatureStatistics.DatasetFeatureDistributions();
        eval.totalImages = 0;

        DistributionComparator.ComparisonResult result = comparator.compare(ref, eval, 0.2, 0.2);
        assertFalse(result.checksAvailable);
        assertEquals("UNAVAILABLE", result.overallSeverity);
    }

    @Test
    void testInvalidStatisticalInputsFiltered() {
        double[] dirtySample1 = new double[]{10.0, Double.NaN, 20.0, Double.POSITIVE_INFINITY, 30.0, 40.0, 50.0};
        double[] dirtySample2 = new double[]{10.0, 20.0, Double.NEGATIVE_INFINITY, 30.0, 40.0, 50.0};

        double[] clean1 = StatisticalUtil.cleanSample(dirtySample1);
        double[] clean2 = StatisticalUtil.cleanSample(dirtySample2);

        assertEquals(5, clean1.length);
        assertEquals(5, clean2.length);

        double psi = StatisticalUtil.calculatePSI(dirtySample1, dirtySample2, 5);
        assertFalse(Double.isNaN(psi), "PSI must not produce NaN on dirty inputs");
        assertFalse(Double.isInfinite(psi), "PSI must not produce Infinity on dirty inputs");

        double ks = StatisticalUtil.kolmogorovSmirnovStatistic(dirtySample1, dirtySample2);
        assertFalse(Double.isNaN(ks), "KS must not produce NaN on dirty inputs");
    }

    @Test
    void testClassDistributionShiftDetection() {
        Map<String, Integer> refClasses = new HashMap<>();
        refClasses.put("pedestrian", 90);
        refClasses.put("car", 10);

        Map<String, Integer> evalClasses = new HashMap<>();
        evalClasses.put("pedestrian", 5);
        evalClasses.put("car", 95); // Major concept shift

        double catPsi = StatisticalUtil.calculateCategoricalPSI(refClasses, evalClasses);
        assertTrue(catPsi > 0.5, "Severe class distribution flip must produce high categorical PSI");

        FeatureStatistics.DatasetFeatureDistributions ref = createValidDist("ref", 10);
        FeatureStatistics.DatasetFeatureDistributions eval = createValidDist("eval", 10);

        DistributionComparator.ComparisonResult result =
                comparator.compareWithClasses(ref, eval, refClasses, evalClasses, 0.2, 0.2);

        assertTrue(result.driftDetected, "Class distribution shift must trigger drift detection");
        assertTrue(result.featureComparisons.containsKey("Class Distribution"));
        assertTrue(result.featureComparisons.get("Class Distribution").driftDetected);
    }

    private FeatureStatistics.DatasetFeatureDistributions createValidDist(String name, int n) {
        FeatureStatistics.DatasetFeatureDistributions d = new FeatureStatistics.DatasetFeatureDistributions();
        d.datasetName = name;
        d.totalImages = n;
        d.brightnessValues = new double[n];
        d.contrastValues = new double[n];
        d.meanIntensityValues = new double[n];
        d.redMeans = new double[n];
        d.greenMeans = new double[n];
        d.blueMeans = new double[n];
        d.aspectRatios = new double[n];
        for (int i = 0; i < n; i++) {
            d.brightnessValues[i] = 50.0 + i;
            d.contrastValues[i] = 20.0 + i;
            d.meanIntensityValues[i] = 50.0 + i;
            d.redMeans[i] = 50.0 + i;
            d.greenMeans[i] = 50.0 + i;
            d.blueMeans[i] = 50.0 + i;
            d.aspectRatios[i] = 1.33;
        }
        return d;
    }
}
