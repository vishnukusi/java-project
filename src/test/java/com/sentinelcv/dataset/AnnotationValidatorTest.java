package com.sentinelcv.dataset;

import com.sentinelcv.enums.FindingSeverity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationValidatorTest {

    private final AnnotationValidator validator = new AnnotationValidator();

    @Test
    void testDetectZeroAreaAndOutOfBounds() {
        DatasetModels.ParseResult parseResult = new DatasetModels.ParseResult();
        parseResult.categories = Map.of(1, "cat");

        DatasetModels.ImageInfo img = new DatasetModels.ImageInfo("img1", "img1.jpg", "img1.jpg");
        img.width = 100;
        img.height = 100;

        // Zero area box
        DatasetModels.AnnotationInfo zeroAreaAnn = new DatasetModels.AnnotationInfo();
        zeroAreaAnn.id = "ann1";
        zeroAreaAnn.imageId = "img1";
        zeroAreaAnn.categoryId = 1;
        zeroAreaAnn.bbox = new DatasetModels.BoundingBox(10, 10, 0, 0, false);
        img.annotations.add(zeroAreaAnn);
        parseResult.annotations.add(zeroAreaAnn);

        // Out of bounds box
        DatasetModels.AnnotationInfo oobAnn = new DatasetModels.AnnotationInfo();
        oobAnn.id = "ann2";
        oobAnn.imageId = "img1";
        oobAnn.categoryId = 1;
        oobAnn.bbox = new DatasetModels.BoundingBox(50, 50, 100, 100, false); // Exceeds 100x100
        img.annotations.add(oobAnn);
        parseResult.annotations.add(oobAnn);

        // Invalid class box
        DatasetModels.AnnotationInfo invalidClassAnn = new DatasetModels.AnnotationInfo();
        invalidClassAnn.id = "ann3";
        invalidClassAnn.imageId = "img1";
        invalidClassAnn.categoryId = 99; // Category 99 does not exist
        invalidClassAnn.bbox = new DatasetModels.BoundingBox(10, 10, 20, 20, false);
        img.annotations.add(invalidClassAnn);
        parseResult.annotations.add(invalidClassAnn);

        parseResult.images.add(img);

        AnnotationValidator.ValidationResult valResult = validator.validate(parseResult, 1L);

        assertEquals(1, valResult.zeroAreaCount);
        assertEquals(1, valResult.outOfBoundsCount);
        assertEquals(1, valResult.invalidClassIdCount);
        assertTrue(valResult.findings.stream().anyMatch(f -> f.getFindingId().contains("ZERO-AREA")));
        assertTrue(valResult.findings.stream().anyMatch(f -> f.getFindingId().contains("OOB")));
        assertTrue(valResult.findings.stream().anyMatch(f -> f.getFindingId().contains("INVALID-CLASS")));
    }
}
