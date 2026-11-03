package com.sentinelcv.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelcv.util.FixtureGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CocoParserTest {

    private final CocoParser parser = new CocoParser(new ObjectMapper());

    @Test
    void testParseValidCocoDataset(@TempDir Path tempDir) throws IOException {
        File datasetDir = FixtureGenerator.createSampleCocoDataset(tempDir, "test_coco");
        assertTrue(parser.canParse(datasetDir));

        DatasetModels.ParseResult result = parser.parse(datasetDir);
        assertNotNull(result);
        assertEquals(3, result.images.size());
        assertEquals(3, result.annotations.size());
        assertEquals(2, result.categories.size());
        assertTrue(result.contributorMetadata.present);
        assertEquals("SentinelCV Research Team", result.contributorMetadata.contributorName);
    }
}
