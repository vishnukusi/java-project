package com.sentinelcv.model;

import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.exception.ModelLoadException;

import java.io.File;

/**
 * Strategy interface for inspecting and running inference on ML models.
 * Supports ONNX and PyTorch/TorchScript models.
 */
public interface ModelLoader {

    /**
     * Determines whether this loader can process the given model file.
     */
    boolean canLoad(File modelFile);

    /**
     * Extracts model metadata, input/output tensor specifications, and graph attributes.
     */
    ModelMetadata inspectMetadata(File modelFile) throws ModelLoadException;

    /**
     * Runs controlled inference using the provided input tensor data and shape.
     */
    float[] runInference(File modelFile, float[] inputData, long[] shape) throws ModelLoadException;

    /**
     * Returns the model format supported by this loader.
     */
    ModelFormat getFormat();
}
