package com.sentinelcv.enums;

/**
 * Status of verification checks in provenance and integrity audits.
 */
public enum VerificationStatus {
    VERIFIED("Verified", "Check passed successfully"),
    FAILED("Failed", "Check detected an issue"),
    UNAVAILABLE("Unavailable", "Check could not be performed"),
    SKIPPED("Skipped", "Check was skipped"),
    PENDING("Pending", "Check has not been executed yet");

    private final String displayName;
    private final String description;

    VerificationStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
