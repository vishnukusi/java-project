package com.sentinelcv.dataset;

import com.sentinelcv.util.FixtureGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class YoloParserTest {

    private final YoloParser parser = new YoloParser();

    @Test
    void testParseValidYoloDataset(@TempDir Path tempDir) throws IOException {
        File datasetDir = FixtureGenerator.createSampleYoloDataset(tempDir, "test_yolo");
        assertTrue(parser.canParse(datasetDir));

        DatasetModels.ParseResult result = parser.parse(datasetDir);
        assertNotNull(result);
        assertEquals(2, result.images.size());
        assertEquals(2, result.annotations.size());
        assertEquals(2, result.categories.size());
        assertEquals("pedestrian", result.categories.get(0));
        assertEquals("automobile", result.categories.get(1));
    }
}
