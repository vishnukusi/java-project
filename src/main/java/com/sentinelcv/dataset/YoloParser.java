package com.sentinelcv.dataset;

import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.exception.DatasetParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Concrete parser for YOLO format datasets.
 * Inspects images directory, corresponding .txt label files, normalized bounding boxes,
 * and parses class definitions from data.yaml, classes.txt, or obj.names.
 */
@Component
public class YoloParser implements DatasetParser {

    private static final Logger logger = LoggerFactory.getLogger(YoloParser.class);
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".bmp", ".webp");

    @Override
    public DatasetFormat getFormat() {
        return DatasetFormat.YOLO;
    }

    @Override
    public boolean canParse(File datasetDir) {
        if (datasetDir == null || !datasetDir.isDirectory()) {
            return false;
        }
        // YOLO datasets typically have an images/ folder, labels/ folder, or .txt annotation files
        File imagesDir = new File(datasetDir, "images");
        File labelsDir = new File(datasetDir, "labels");
        if (imagesDir.exists() || labelsDir.exists()) {
            return true;
        }

        // Check if there are txt files matching image files
        try (Stream<Path> stream = Files.walk(datasetDir.toPath(), 2)) {
            return stream.anyMatch(p -> p.toString().endsWith(".txt") &&
                    !p.getFileName().toString().equalsIgnoreCase("classes.txt") &&
                    !p.getFileName().toString().equalsIgnoreCase("requirements.txt"));
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public DatasetModels.ParseResult parse(File datasetDir) throws DatasetParsingException {
        DatasetModels.ParseResult result = new DatasetModels.ParseResult();
        result.datasetName = datasetDir.getName();
        result.format = "YOLO";

        // 1. Parse Class Names (classes.txt, data.yaml, obj.names)
        loadYoloClasses(datasetDir, result.categories);

        // 2. Discover Images
        List<File> imageFiles = findImageFiles(datasetDir);
        if (imageFiles.isEmpty()) {
            throw new DatasetParsingException("No image files found in YOLO dataset directory: " + datasetDir.getAbsolutePath());
        }

        int annIdCounter = 1;
        for (File imgFile : imageFiles) {
            String baseName = getBaseName(imgFile.getName());
            String relPath = datasetDir.toPath().relativize(imgFile.toPath()).toString();

            DatasetModels.ImageInfo imgInfo = new DatasetModels.ImageInfo(baseName, imgFile.getName(), relPath);
            imgInfo.fileSizeBytes = imgFile.length();

            // Try reading dimensions via ImageIO
            try {
                BufferedImage bimg = ImageIO.read(imgFile);
                if (bimg != null) {
                    imgInfo.width = bimg.getWidth();
                    imgInfo.height = bimg.getHeight();
                } else {
                    result.parsingWarnings.add("Could not decode image header for: " + imgFile.getName());
                }
            } catch (Exception e) {
                result.parsingErrors.add("Broken image file: " + imgFile.getName() + " (" + e.getMessage() + ")");
            }

            // Find corresponding label file
            File labelFile = findLabelFile(datasetDir, imgFile, baseName);
            if (labelFile != null && labelFile.exists()) {
                parseYoloLabelFile(labelFile, imgInfo, result, annIdCounter);
                annIdCounter += imgInfo.annotations.size();
            } else {
                result.parsingWarnings.add("Missing label file for image: " + imgFile.getName());
            }

            result.images.add(imgInfo);
            result.annotations.addAll(imgInfo.annotations);
        }

        // Check contributor metadata (YOLO typically lacks contributor metadata)
        result.contributorMetadata.present = false;

        logger.info("YOLO parse complete: {} images, {} annotations, {} categories",
                result.images.size(), result.annotations.size(), result.categories.size());
        return result;
    }

    private void parseYoloLabelFile(File labelFile, DatasetModels.ImageInfo imgInfo,
                                   DatasetModels.ParseResult result, int startAnnId) {
        try (BufferedReader reader = new BufferedReader(new FileReader(labelFile))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                if (parts.length < 5) {
                    result.parsingWarnings.add(String.format("Malformed YOLO annotation line %d in %s: '%s'",
                            lineNum, labelFile.getName(), line));
                    continue;
                }

                try {
                    int classId = Integer.parseInt(parts[0]);
                    double xCenter = Double.parseDouble(parts[1]);
                    double yCenter = Double.parseDouble(parts[2]);
                    double width = Double.parseDouble(parts[3]);
                    double height = Double.parseDouble(parts[4]);

                    DatasetModels.AnnotationInfo ann = new DatasetModels.AnnotationInfo();
                    ann.id = String.valueOf(startAnnId + imgInfo.annotations.size());
                    ann.imageId = imgInfo.id;
                    ann.categoryId = classId;
                    ann.categoryName = result.categories.computeIfAbsent(classId, id -> "Class_" + id);
                    ann.bbox = new DatasetModels.BoundingBox(xCenter, yCenter, width, height, true);

                    imgInfo.annotations.add(ann);
                } catch (NumberFormatException e) {
                    result.parsingWarnings.add("Invalid numeric value in " + labelFile.getName() + " line " + lineNum);
                }
            }
        } catch (IOException e) {
            result.parsingErrors.add("Error reading label file " + labelFile.getName() + ": " + e.getMessage());
        }
    }

    private List<File> findImageFiles(File dir) {
        try (Stream<Path> stream = Files.walk(dir.toPath(), 5)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return IMAGE_EXTENSIONS.stream().anyMatch(name::endsWith);
                    })
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private File findLabelFile(File datasetDir, File imgFile, String baseName) {
        // Option 1: alongside the image with .txt
        File sameDir = new File(imgFile.getParentFile(), baseName + ".txt");
        if (sameDir.exists()) return sameDir;

        // Option 2: in parallel labels/ directory
        String pathStr = imgFile.getAbsolutePath();
        if (pathStr.contains("/images/")) {
            String labelPath = pathStr.replace("/images/", "/labels/");
            File parallel = new File(labelPath.substring(0, labelPath.lastIndexOf('.')) + ".txt");
            if (parallel.exists()) return parallel;
        }

        // Option 3: search within labels/
        File labelsFolder = new File(datasetDir, "labels");
        if (labelsFolder.exists()) {
            File direct = new File(labelsFolder, baseName + ".txt");
            if (direct.exists()) return direct;
        }

        return null;
    }

    private void loadYoloClasses(File datasetDir, Map<Integer, String> categories) {
        // Try classes.txt or obj.names
        File classesFile = new File(datasetDir, "classes.txt");
        if (!classesFile.exists()) {
            classesFile = new File(datasetDir, "obj.names");
        }

        if (classesFile.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(classesFile))) {
                String line;
                int id = 0;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        categories.put(id++, line);
                    }
                }
            } catch (IOException ignored) {}
        }

        // Try data.yaml
        File yamlFile = new File(datasetDir, "data.yaml");
        if (yamlFile.exists() && categories.isEmpty()) {
            try (BufferedReader r = new BufferedReader(new FileReader(yamlFile))) {
                String line;
                boolean inNames = false;
                int id = 0;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("names:")) {
                        inNames = true;
                        // Single-line format: names: ['car', 'person', 'dog']
                        if (line.contains("[") && line.contains("]")) {
                            String content = line.substring(line.indexOf('[') + 1, line.indexOf(']'));
                            String[] items = content.split(",");
                            for (String item : items) {
                                String clean = item.trim().replace("'", "").replace("\"", "");
                                if (!clean.isEmpty()) {
                                    categories.put(id++, clean);
                                }
                            }
                            break;
                        }
                        continue;
                    }
                    if (inNames) {
                        if (line.startsWith("-")) {
                            String name = line.substring(1).trim().replace("'", "").replace("\"", "");
                            categories.put(id++, name);
                        } else if (line.contains(":") && !line.startsWith("names")) {
                            // Map format: 0: person
                            String[] kv = line.split(":", 2);
                            try {
                                int cid = Integer.parseInt(kv[0].trim());
                                categories.put(cid, kv[1].trim().replace("'", "").replace("\"", ""));
                            } catch (NumberFormatException ignored) {}
                        } else if (line.matches("^[a-zA-Z].*")) {
                            inNames = false;
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    private String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
