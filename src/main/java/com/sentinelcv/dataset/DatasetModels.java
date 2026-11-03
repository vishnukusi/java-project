package com.sentinelcv.dataset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Objects for parsed dataset representation.
 */
public class DatasetModels {

    public static class BoundingBox {
        public double x;
        public double y;
        public double width;
        public double height;
        public boolean isNormalized; // True for YOLO (0..1), False for COCO (absolute pixels)

        public BoundingBox() {}

        public BoundingBox(double x, double y, double width, double height, boolean isNormalized) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.isNormalized = isNormalized;
        }

        public double getArea(int imageWidth, int imageHeight) {
            if (isNormalized) {
                return (width * imageWidth) * (height * imageHeight);
            }
            return width * height;
        }

        public boolean isZeroArea() {
            return width <= 0 || height <= 0;
        }

        public boolean isOutOfBounds(int imgW, int imgH) {
            if (isNormalized) {
                // In YOLO, (x, y) is center of box
                double xmin = x - width / 2.0;
                double ymin = y - height / 2.0;
                double xmax = x + width / 2.0;
                double ymax = y + height / 2.0;
                return xmin < -0.01 || ymin < -0.01 || xmax > 1.01 || ymax > 1.01 || width > 1.01 || height > 1.01;
            } else {
                // In COCO, [x, y, width, height] top-left
                return x < 0 || y < 0 || (x + width) > imgW || (y + height) > imgH;
            }
        }
    }

    public static class ImageInfo {
        public String id;
        public String fileName;
        public String relativePath;
        public int width;
        public int height;
        public long fileSizeBytes;
        public String sha256;
        public String dHash;
        public List<AnnotationInfo> annotations = new ArrayList<>();

        public ImageInfo() {}
        public ImageInfo(String id, String fileName, String relativePath) {
            this.id = id;
            this.fileName = fileName;
            this.relativePath = relativePath;
        }
    }

    public static class AnnotationInfo {
        public String id;
        public String imageId;
        public int categoryId;
        public String categoryName;
        public BoundingBox bbox;
        public double confidence = 1.0;
        public Map<String, Object> attributes = new HashMap<>();

        public AnnotationInfo() {}
    }

    public static class ContributorMetadata {
        public boolean present = false;
        public String contributorId;
        public String contributorName;
        public String organization;
        public String timestamp;
        public Map<String, Integer> sampleCountsByContributor = new HashMap<>();
    }

    public static class ParseResult {
        public String datasetName;
        public String format;
        public List<ImageInfo> images = new ArrayList<>();
        public Map<Integer, String> categories = new HashMap<>();
        public List<AnnotationInfo> annotations = new ArrayList<>();
        public ContributorMetadata contributorMetadata = new ContributorMetadata();
        public List<String> parsingErrors = new ArrayList<>();
        public List<String> parsingWarnings = new ArrayList<>();
    }
}
