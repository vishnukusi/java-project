package com.sentinelcv.enums;

/**
 * Severity levels for security findings, ordered from most to least critical.
 */
public enum FindingSeverity {
    CRITICAL(5, "Critical", "#dc3545"),
    HIGH(4, "High", "#fd7e14"),
    MEDIUM(3, "Medium", "#ffc107"),
    LOW(2, "Low", "#17a2b8"),
    INFO(1, "Info", "#6c757d");

    private final int weight;
    private final String displayName;
    private final String color;

    FindingSeverity(int weight, String displayName, String color) {
        this.weight = weight;
        this.displayName = displayName;
        this.color = color;
    }

    public int getWeight() { return weight; }
    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
}
