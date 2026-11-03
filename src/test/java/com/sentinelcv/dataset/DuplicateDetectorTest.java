package com.sentinelcv.dataset;

import com.sentinelcv.util.FixtureGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateDetectorTest {

    private final DuplicateDetector detector = new DuplicateDetector();

    @Test
    void testDetectExactDuplicates(@TempDir Path tempDir) throws IOException {
        File datasetDir = FixtureGenerator.createSampleCocoDataset(tempDir, "test_dup");

        DatasetModels.ImageInfo img1 = new DatasetModels.ImageInfo("img1", "sample1.jpg", "images/sample1.jpg");
        DatasetModels.ImageInfo img2 = new DatasetModels.ImageInfo("img2", "sample2.jpg", "images/sample2.jpg");
        DatasetModels.ImageInfo img3 = new DatasetModels.ImageInfo("img3", "sample3.jpg", "images/sample3.jpg"); // Identical to sample1

        DuplicateDetector.DuplicateResult result = detector.detectDuplicates(datasetDir, List.of(img1, img2, img3), 10, 1L);

        assertNotNull(result);
        assertEquals(1, result.totalExactDuplicates);
        assertEquals(1, result.exactDuplicateGroups.size());
        assertTrue(result.findings.stream().anyMatch(f -> f.getFindingId().contains("EXACT-DUP")));
    }
}
