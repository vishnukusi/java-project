package com.sentinelcv.enums;

/**
 * Overall assessment decision for an audited asset.
 */
public enum AssessmentDecision {
    ACCEPT("Accept", "Asset meets integrity requirements", "#28a745"),
    REVIEW("Review", "Asset requires human review before use", "#ffc107"),
    QUARANTINE("Quarantine", "Asset should not be used without thorough investigation", "#dc3545");

    private final String displayName;
    private final String description;
    private final String color;

    AssessmentDecision(String displayName, String description, String color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getColor() { return color; }
}
