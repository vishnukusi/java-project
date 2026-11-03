package com.sentinelcv.model;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.exception.ModelLoadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;

/**
 * Concrete ModelLoader implementation for Open Neural Network Exchange (ONNX) models
 * using Microsoft ONNX Runtime Java API.
 */
@Component
public class OnnxModelLoader implements ModelLoader {

    private static final Logger logger = LoggerFactory.getLogger(OnnxModelLoader.class);
    private final OrtEnvironment env;

    public OnnxModelLoader() {
        this.env = OrtEnvironment.getEnvironment();
    }

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.ONNX;
    }

    @Override
    public boolean canLoad(File modelFile) {
        if (modelFile == null || !modelFile.isFile()) {
            return false;
        }
        return modelFile.getName().toLowerCase().endsWith(".onnx");
    }

    @Override
    public ModelMetadata inspectMetadata(File modelFile) throws ModelLoadException {
        ModelMetadata meta = new ModelMetadata();
        meta.format = "ONNX";

        try (OrtSession session = env.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions())) {
            meta.inferenceSupported = true;

            // Extract input info
            for (Map.Entry<String, NodeInfo> entry : session.getInputInfo().entrySet()) {
                String inputName = entry.getKey();
                NodeInfo info = entry.getValue();
                String dataType = info.getInfo().toString();
                long[] shape = new long[0];
                if (info.getInfo() instanceof TensorInfo ti) {
                    shape = ti.getShape();
                    dataType = ti.type.toString();
                }
                meta.inputTensors.add(new ModelMetadata.TensorInfo(inputName, dataType, shape));
            }

            // Extract output info
            for (Map.Entry<String, NodeInfo> entry : session.getOutputInfo().entrySet()) {
                String outputName = entry.getKey();
                NodeInfo info = entry.getValue();
                String dataType = info.getInfo().toString();
                long[] shape = new long[0];
                if (info.getInfo() instanceof TensorInfo ti) {
                    shape = ti.getShape();
                    dataType = ti.type.toString();
                }
                meta.outputTensors.add(new ModelMetadata.TensorInfo(outputName, dataType, shape));
            }

            logger.info("ONNX model {} inspected: {} inputs, {} outputs",
                    modelFile.getName(), meta.inputTensors.size(), meta.outputTensors.size());
            return meta;

        } catch (Exception e) {
            logger.warn("Failed to inspect ONNX model {}: {}", modelFile.getName(), e.getMessage());
            meta.inferenceSupported = false;
            meta.loadErrorMessage = e.getMessage();
            return meta;
        }
    }

    @Override
    public float[] runInference(File modelFile, float[] inputData, long[] shape) throws ModelLoadException {
        try (OrtSession session = env.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions())) {
            String inputName = session.getInputNames().iterator().next();

            FloatBuffer buffer = FloatBuffer.wrap(inputData);
            try (OnnxTensor inputTensor = OnnxTensor.createTensor(env, buffer, shape);
                 OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {

                Object outputVal = result.get(0).getValue();
                return flattenOutput(outputVal);
            }
        } catch (Exception e) {
            throw new ModelLoadException("ONNX inference failed: " + e.getMessage(), e);
        }
    }

    /**
     * Flattens multi-dimensional output arrays of any dimensionality (1D, 2D, 3D, 4D, etc.)
     * into a single continuous 1D float array safely without memory or buffer exceptions.
     */
    public static float[] flattenOutput(Object outputVal) {
        if (outputVal == null) return new float[0];
        if (outputVal instanceof float[] f1) return f1;

        java.util.List<Float> list = new java.util.ArrayList<>();
        flattenRecursively(outputVal, list);
        float[] flat = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            flat[i] = list.get(i);
        }
        return flat;
    }

    private static void flattenRecursively(Object obj, java.util.List<Float> collector) {
        if (obj instanceof float[] fa) {
            for (float f : fa) collector.add(f);
        } else if (obj instanceof double[] da) {
            for (double d : da) collector.add((float) d);
        } else if (obj instanceof Object[] oa) {
            for (Object sub : oa) {
                flattenRecursively(sub, collector);
            }
        }
    }
}
