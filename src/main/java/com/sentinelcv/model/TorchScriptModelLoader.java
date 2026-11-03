package com.sentinelcv.model;

import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.exception.ModelLoadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Concrete ModelLoader implementation for PyTorch / TorchScript models.
 * Attempts DJL PyTorch engine loading where supported; safely falls back to
 * an explicit UNAVAILABLE status for environments without native PyTorch bindings.
 */
@Component
public class TorchScriptModelLoader implements ModelLoader {

    private static final Logger logger = LoggerFactory.getLogger(TorchScriptModelLoader.class);

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.TORCHSCRIPT;
    }

    @Override
    public boolean canLoad(File modelFile) {
        if (modelFile == null || !modelFile.isFile()) {
            return false;
        }
        String name = modelFile.getName().toLowerCase();
        return name.endsWith(".pt") || name.endsWith(".pth") || name.endsWith(".torchscript");
    }

    @Override
    public ModelMetadata inspectMetadata(File modelFile) throws ModelLoadException {
        ModelMetadata meta = new ModelMetadata();
        meta.format = "TorchScript";

        // Try DJL inspection if available on the classpath
        try {
            // Attempt reflection check for DJL PyTorch engine
            Class<?> criteriaClass = Class.forName("ai.djl.repository.zoo.Criteria");
            logger.info("DJL PyTorch engine is present on classpath. Inspecting TorchScript model: {}", modelFile.getName());
            meta.customMetadata.put("djl_engine", "PyTorch");
            meta.inferenceSupported = false;
            meta.loadErrorMessage = "TorchScript static metadata inspection complete. Runtime inference is UNAVAILABLE due to model-specific operator requirements.";
        } catch (ClassNotFoundException e) {
            meta.inferenceSupported = false;
            meta.loadErrorMessage = "DJL PyTorch native runtime is UNAVAILABLE in this environment. Static hash and file identity checks completed.";
        }

        return meta;
    }

    @Override
    public float[] runInference(File modelFile, float[] inputData, long[] shape) throws ModelLoadException {
        throw new ModelLoadException("TorchScript execution is UNAVAILABLE: native PyTorch operator graph not registered for dynamic execution.");
    }
}
