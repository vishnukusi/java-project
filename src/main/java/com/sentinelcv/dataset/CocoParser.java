package com.sentinelcv.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.exception.DatasetParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Concrete parser for Microsoft COCO format datasets.
 * Parses JSON annotations, resolves image files, extracts categories and contributor metadata.
 */
@Component
public class CocoParser implements DatasetParser {

    private static final Logger logger = LoggerFactory.getLogger(CocoParser.class);
    private final ObjectMapper objectMapper;

    public CocoParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public DatasetFormat getFormat() {
        return DatasetFormat.COCO;
    }

    @Override
    public boolean canParse(File datasetDir) {
        if (datasetDir == null || !datasetDir.isDirectory()) {
            return false;
        }
        return findCocoJsonFile(datasetDir).isPresent();
    }

    @Override
    public DatasetModels.ParseResult parse(File datasetDir) throws DatasetParsingException {
        DatasetModels.ParseResult result = new DatasetModels.ParseResult();
        result.datasetName = datasetDir.getName();
        result.format = "COCO";

        Optional<File> jsonFileOpt = findCocoJsonFile(datasetDir);
        if (jsonFileOpt.isEmpty()) {
            throw new DatasetParsingException("No valid COCO JSON annotation file found in " + datasetDir.getAbsolutePath());
        }

        File jsonFile = jsonFileOpt.get();
        logger.info("Parsing COCO annotation file: {}", jsonFile.getAbsolutePath());

        JsonNode root;
        try {
            root = objectMapper.readTree(jsonFile);
        } catch (IOException e) {
            throw new DatasetParsingException("Failed to read JSON: " + e.getMessage(), e);
        }

        // Validate top-level structure
        if (!root.has("images")) {
            result.parsingErrors.add("Missing required 'images' array in COCO JSON");
        }
        if (!root.has("annotations")) {
            result.parsingWarnings.add("Missing or empty 'annotations' array in COCO JSON");
        }

        // 1. Parse Info / Contributor Metadata
        if (root.has("info")) {
            JsonNode info = root.get("info");
            if (info.has("contributor") && !info.get("contributor").asText().isBlank()) {
                result.contributorMetadata.present = true;
                result.contributorMetadata.contributorName = info.get("contributor").asText();
            }
            if (info.has("description")) {
                result.contributorMetadata.organization = info.get("description").asText();
            }
            if (info.has("date_created")) {
                result.contributorMetadata.timestamp = info.get("date_created").asText();
            }
        }

        // 2. Parse Categories
        if (root.has("categories") && root.get("categories").isArray()) {
            for (JsonNode catNode : root.get("categories")) {
                if (catNode.has("id") && catNode.has("name")) {
                    int catId = catNode.get("id").asInt();
                    String catName = catNode.get("name").asText();
                    result.categories.put(catId, catName);
                }
            }
        }

        // 3. Parse Images
        Map<String, DatasetModels.ImageInfo> imageMap = new HashMap<>();
        if (root.has("images") && root.get("images").isArray()) {
            for (JsonNode imgNode : root.get("images")) {
                String imgId = imgNode.has("id") ? imgNode.get("id").asText() : "";
                String fileName = imgNode.has("file_name") ? imgNode.get("file_name").asText() : "";
                int width = imgNode.has("width") ? imgNode.get("width").asInt() : 0;
                int height = imgNode.has("height") ? imgNode.get("height").asInt() : 0;

                if (imgId.isEmpty() || fileName.isEmpty()) {
                    result.parsingWarnings.add("Skipping image with missing id or file_name");
                    continue;
                }

                // Check if contributor is attached per image
                if (imgNode.has("contributor")) {
                    result.contributorMetadata.present = true;
                    String contrib = imgNode.get("contributor").asText();
                    result.contributorMetadata.sampleCountsByContributor.merge(contrib, 1, Integer::sum);
                }

                File foundImageFile = resolveImageFile(datasetDir, fileName);
                String relativePath = foundImageFile != null ?
                        datasetDir.toPath().relativize(foundImageFile.toPath()).toString() : fileName;

                DatasetModels.ImageInfo imgInfo = new DatasetModels.ImageInfo(imgId, fileName, relativePath);
                imgInfo.width = width;
                imgInfo.height = height;
                if (foundImageFile != null && foundImageFile.exists()) {
                    imgInfo.fileSizeBytes = foundImageFile.length();
                } else {
                    result.parsingErrors.add("Image file referenced in COCO JSON not found on disk: " + fileName);
                }

                imageMap.put(imgId, imgInfo);
                result.images.add(imgInfo);
            }
        }

        // 4. Parse Annotations
        if (root.has("annotations") && root.get("annotations").isArray()) {
            for (JsonNode annNode : root.get("annotations")) {
                String annId = annNode.has("id") ? annNode.get("id").asText() : "";
                String imgId = annNode.has("image_id") ? annNode.get("image_id").asText() : "";
                int catId = annNode.has("category_id") ? annNode.get("category_id").asInt() : -1;

                DatasetModels.AnnotationInfo annInfo = new DatasetModels.AnnotationInfo();
                annInfo.id = annId;
                annInfo.imageId = imgId;
                annInfo.categoryId = catId;
                annInfo.categoryName = result.categories.getOrDefault(catId, "Class_" + catId);

                if (annNode.has("bbox") && annNode.get("bbox").isArray()) {
                    JsonNode bboxNode = annNode.get("bbox");
                    if (bboxNode.size() >= 4) {
                        double x = bboxNode.get(0).asDouble();
                        double y = bboxNode.get(1).asDouble();
                        double w = bboxNode.get(2).asDouble();
                        double h = bboxNode.get(3).asDouble();
                        annInfo.bbox = new DatasetModels.BoundingBox(x, y, w, h, false);
                    }
                }

                if (annInfo.bbox == null) {
                    result.parsingWarnings.add("Annotation " + annId + " has missing or malformed bbox");
                }

                result.annotations.add(annInfo);

                DatasetModels.ImageInfo imgInfo = imageMap.get(imgId);
                if (imgInfo != null) {
                    imgInfo.annotations.add(annInfo);
                } else {
                    result.parsingWarnings.add("Annotation references non-existent image ID: " + imgId);
                }
            }
        }

        logger.info("COCO parse complete: {} images, {} annotations, {} categories",
                result.images.size(), result.annotations.size(), result.categories.size());
        return result;
    }

    private Optional<File> findCocoJsonFile(File dir) {
        try (Stream<Path> stream = Files.walk(dir.toPath(), 3)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("._"))
                    .filter(p -> p.toString().toLowerCase().endsWith(".json"))
                    .sorted()
                    .map(Path::toFile)
                    .filter(this::looksLikeCocoJson)
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private boolean looksLikeCocoJson(File file) {
        try {
            JsonNode node = objectMapper.readTree(file);
            return node.has("images") && (node.has("annotations") || node.has("categories"));
        } catch (Exception e) {
            return false;
        }
    }

    private File resolveImageFile(File datasetDir, String fileName) {
        // Direct child
        File direct = new File(datasetDir, fileName);
        if (direct.exists()) return direct;

        // In images/ subfolder
        File inImages = new File(new File(datasetDir, "images"), fileName);
        if (inImages.exists()) return inImages;

        // Deep search by filename
        try (Stream<Path> stream = Files.walk(datasetDir.toPath(), 4)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(new File(fileName).getName()))
                    .map(Path::toFile)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
