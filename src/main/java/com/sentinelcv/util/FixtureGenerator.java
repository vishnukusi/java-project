package com.sentinelcv.util;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility for generating synthetic test datasets (COCO and YOLO) with real decoded images.
 * Used for testing and initial offline system demonstration.
 */
public final class FixtureGenerator {

    private FixtureGenerator() {}

    /**
     * Creates a test image with distinct visual patterns.
     */
    public static BufferedImage createPatternImage(int width, int height, Color bgColor, Color patternColor) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(bgColor);
        g.fillRect(0, 0, width, height);

        g.setColor(patternColor);
        g.fillRect(width / 4, height / 4, width / 2, height / 2);
        g.setColor(Color.WHITE);
        g.drawString("SentinelCV", 10, height / 2);
        g.dispose();
        return image;
    }

    /**
     * Generates a sample COCO dataset directory on disk.
     */
    public static File createSampleCocoDataset(Path baseDir, String datasetName) throws IOException {
        Path datasetPath = baseDir.resolve(datasetName);
        Path imagesPath = datasetPath.resolve("images");
        Files.createDirectories(imagesPath);

        // 1. Generate 3 image files
        BufferedImage img1 = createPatternImage(300, 200, Color.BLUE, Color.YELLOW);
        BufferedImage img2 = createPatternImage(400, 300, Color.RED, Color.GREEN);
        BufferedImage img3 = createPatternImage(300, 200, Color.BLUE, Color.YELLOW); // Identical pattern to img1

        File f1 = imagesPath.resolve("sample1.jpg").toFile();
        File f2 = imagesPath.resolve("sample2.jpg").toFile();
        File f3 = imagesPath.resolve("sample3.jpg").toFile(); // Duplicate of sample1
        ImageIO.write(img1, "jpg", f1);
        ImageIO.write(img2, "jpg", f2);
        ImageIO.write(img3, "jpg", f3);

        // 2. Generate COCO JSON
        String cocoJson = """
                {
                  "info": {
                    "description": "SentinelCV Sample COCO Benchmark",
                    "contributor": "SentinelCV Research Team",
                    "date_created": "2026-11-02"
                  },
                  "categories": [
                    { "id": 1, "name": "vehicle", "supercategory": "object" },
                    { "id": 2, "name": "person", "supercategory": "object" }
                  ],
                  "images": [
                    { "id": "img1", "file_name": "sample1.jpg", "width": 300, "height": 200 },
                    { "id": "img2", "file_name": "sample2.jpg", "width": 400, "height": 300 },
                    { "id": "img3", "file_name": "sample3.jpg", "width": 300, "height": 200 }
                  ],
                  "annotations": [
                    { "id": "ann1", "image_id": "img1", "category_id": 1, "bbox": [50.0, 40.0, 100.0, 80.0] },
                    { "id": "ann2", "image_id": "img2", "category_id": 2, "bbox": [30.0, 20.0, 150.0, 200.0] },
                    { "id": "ann3", "image_id": "img3", "category_id": 1, "bbox": [50.0, 40.0, 100.0, 80.0] }
                  ]
                }
                """;

        File jsonFile = datasetPath.resolve("annotations.json").toFile();
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write(cocoJson);
        }

        return datasetPath.toFile();
    }

    /**
     * Generates a sample YOLO dataset directory on disk.
     */
    public static File createSampleYoloDataset(Path baseDir, String datasetName) throws IOException {
        Path datasetPath = baseDir.resolve(datasetName);
        Path imagesPath = datasetPath.resolve("images");
        Path labelsPath = datasetPath.resolve("labels");
        Files.createDirectories(imagesPath);
        Files.createDirectories(labelsPath);

        // Images
        BufferedImage img1 = createPatternImage(320, 240, Color.BLACK, Color.WHITE);
        BufferedImage img2 = createPatternImage(320, 240, Color.CYAN, Color.MAGENTA);
        ImageIO.write(img1, "jpg", imagesPath.resolve("img_01.jpg").toFile());
        ImageIO.write(img2, "jpg", imagesPath.resolve("img_02.jpg").toFile());

        // Labels (class_id x_center y_center width height)
        Files.writeString(labelsPath.resolve("img_01.txt"), "0 0.5 0.5 0.4 0.4\n");
        Files.writeString(labelsPath.resolve("img_02.txt"), "1 0.4 0.4 0.3 0.5\n");

        // data.yaml
        String yaml = "names:\n  0: pedestrian\n  1: automobile\n";
        Files.writeString(datasetPath.resolve("data.yaml"), yaml);

        return datasetPath.toFile();
    }

    /**
     * Bundles a dataset directory into a ZIP file.
     */
    public static File zipDirectory(File sourceDir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Path sourcePath = sourceDir.toPath();
            Files.walk(sourcePath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(path).toString());
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        return zipFile;
    }

    /**
     * Minimal pure-Java protobuf writer for generating compliant ONNX models without external tooling.
     */
    public static class ProtoWriter {
        private final java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

        public void writeVarint(long value) {
            while ((value & ~0x7FL) != 0) {
                baos.write((int) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
            baos.write((int) (value & 0x7F));
        }

        public void writeTag(int fieldNumber, int wireType) {
            writeVarint(((long) fieldNumber << 3) | wireType);
        }

        public void writeInt64(int fieldNumber, long value) {
            writeTag(fieldNumber, 0);
            writeVarint(value);
        }

        public void writeString(int fieldNumber, String s) {
            byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            writeTag(fieldNumber, 2);
            writeVarint(bytes.length);
            baos.write(bytes, 0, bytes.length);
        }

        public void writeBytes(int fieldNumber, byte[] bytes) {
            writeTag(fieldNumber, 2);
            writeVarint(bytes.length);
            baos.write(bytes, 0, bytes.length);
        }

        public void writeMessage(int fieldNumber, ProtoWriter msg) {
            writeBytes(fieldNumber, msg.toByteArray());
        }

        public byte[] toByteArray() {
            return baos.toByteArray();
        }
    }

    /**
     * Generates a genuine, valid, small ONNX model file that ONNX Runtime can load and execute.
     */
    public static File createSampleOnnxModel(File destinationFile, long[] shape, String opType) throws IOException {
        ProtoWriter graph = new ProtoWriter();

        // 1. NodeProto
        ProtoWriter node = new ProtoWriter();
        node.writeString(1, "input");
        node.writeString(2, "output");
        node.writeString(3, "op0");
        node.writeString(4, opType != null ? opType : "Relu");
        graph.writeMessage(1, node);

        // 2. Graph Name
        graph.writeString(2, "sentinelcv_vision_graph");

        // TensorShapeProto
        ProtoWriter tensorShape = new ProtoWriter();
        for (long dim : shape) {
            ProtoWriter dimMsg = new ProtoWriter();
            dimMsg.writeInt64(1, dim);
            tensorShape.writeMessage(1, dimMsg);
        }

        // TypeProto (FLOAT = 1)
        ProtoWriter tensorType = new ProtoWriter();
        tensorType.writeInt64(1, 1);
        tensorType.writeMessage(2, tensorShape);

        ProtoWriter typeProto = new ProtoWriter();
        typeProto.writeMessage(1, tensorType);

        // Graph Input ValueInfoProto
        ProtoWriter inputInfo = new ProtoWriter();
        inputInfo.writeString(1, "input");
        inputInfo.writeMessage(2, typeProto);
        graph.writeMessage(11, inputInfo);

        // Graph Output ValueInfoProto
        ProtoWriter outputInfo = new ProtoWriter();
        outputInfo.writeString(1, "output");
        outputInfo.writeMessage(2, typeProto);
        graph.writeMessage(12, outputInfo);

        // ModelProto
        ProtoWriter model = new ProtoWriter();
        model.writeInt64(1, 8); // ir_version 8
        model.writeString(2, "SentinelCV-Auditor");
        model.writeString(3, "1.0.0");
        model.writeString(4, "com.sentinelcv");
        model.writeInt64(5, 1); // model_version
        model.writeString(6, "SentinelCV Verified Baseline Vision Model - Apache 2.0 License");

        // Opset 17
        ProtoWriter opset = new ProtoWriter();
        opset.writeString(1, "");
        opset.writeInt64(2, 17);
        model.writeMessage(8, opset);

        // Attach graph
        model.writeMessage(7, graph);

        if (destinationFile.getParentFile() != null) {
            destinationFile.getParentFile().mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(destinationFile)) {
            fos.write(model.toByteArray());
        }
        return destinationFile;
    }

    public static File createSampleOnnxModel(File destinationFile) throws IOException {
        return createSampleOnnxModel(destinationFile, new long[]{1, 3, 224, 224}, "Relu");
    }
}
