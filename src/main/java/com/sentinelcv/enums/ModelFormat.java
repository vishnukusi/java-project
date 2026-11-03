package com.sentinelcv.enums;

/**
 * Supported model formats for inspection and behavioral auditing.
 */
public enum ModelFormat {
    ONNX("ONNX", "Open Neural Network Exchange format"),
    TORCHSCRIPT("TorchScript", "PyTorch TorchScript serialized model"),
    UNKNOWN("Unknown", "Unrecognized model format");

    private final String displayName;
    private final String description;

    ModelFormat(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
