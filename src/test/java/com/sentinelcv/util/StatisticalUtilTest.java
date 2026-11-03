package com.sentinelcv.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatisticalUtilTest {

    @Test
    void testPsiIdenticalDistributions() {
        double[] sample = new double[]{10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0};
        double psi = StatisticalUtil.calculatePSI(sample, sample, 5);
        // Identical distributions should yield virtually 0 PSI
        assertTrue(psi < 0.05, "PSI for identical samples should be near zero, got: " + psi);
    }

    @Test
    void testPsiShiftedDistributions() {
        double[] ref = new double[]{1.0, 2.0, 3.0, 4.0, 5.0};
        double[] shifted = new double[]{10.0, 12.0, 15.0, 18.0, 20.0};
        double psi = StatisticalUtil.calculatePSI(ref, shifted, 5);
        assertTrue(psi > 0.1, "PSI for shifted samples should detect significant shift, got: " + psi);
    }

    @Test
    void testKolmogorovSmirnovStatistic() {
        double[] sample1 = new double[]{1.0, 2.0, 3.0, 4.0, 5.0};
        double[] sample2 = new double[]{1.0, 2.0, 3.0, 4.0, 5.0};
        double dIdentical = StatisticalUtil.kolmogorovSmirnovStatistic(sample1, sample2);
        assertEquals(0.0, dIdentical, 1e-4);

        double[] sampleDifferent = new double[]{10.0, 20.0, 30.0, 40.0, 50.0};
        double dDifferent = StatisticalUtil.kolmogorovSmirnovStatistic(sample1, sampleDifferent);
        assertEquals(1.0, dDifferent, 1e-4); // Completely disjoint
    }
}
