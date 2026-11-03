package com.sentinelcv.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object representing inspected model metadata and tensor specifications.
 */
public class ModelMetadata {

    public String format;
    public String producerName;
    public String graphName;
    public long version;
    public Map<String, String> customMetadata = new HashMap<>();

    public List<TensorInfo> inputTensors = new ArrayList<>();
    public List<TensorInfo> outputTensors = new ArrayList<>();
    public boolean inferenceSupported = false;
    public String loadErrorMessage;

    public static class TensorInfo {
        public String name;
        public String dataType;
        public long[] shape;

        public TensorInfo() {}
        public TensorInfo(String name, String dataType, long[] shape) {
            this.name = name;
            this.dataType = dataType;
            this.shape = shape;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(name).append(" (").append(dataType).append(")[");
            if (shape != null) {
                for (int i = 0; i < shape.length; i++) {
                    sb.append(shape[i]);
                    if (i < shape.length - 1) sb.append(", ");
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }
}
