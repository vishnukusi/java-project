package com.sentinelcv.enums;

/**
 * Supported dataset formats for parsing and auditing.
 */
public enum DatasetFormat {
    COCO("COCO", "Microsoft COCO JSON format"),
    YOLO("YOLO", "YOLO text-based annotation format");

    private final String displayName;
    private final String description;

    DatasetFormat(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
