package com.sentinelcv.model;

import com.sentinelcv.entity.MlModel;
import com.sentinelcv.util.HashUtil;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * Generates and validates cryptographic and structural fingerprints for ML models.
 * Detects silent file substitution and integrity modifications.
 */
@Component
public class ModelFingerprintService {

    public static class FingerprintResult {
        public String sha256;
        public long fileSizeBytes;
        public String structuralFingerprint;
        public boolean identityMatches;
        public String statusDescription;
    }

    /**
     * Computes the cryptographic and structural fingerprint of a model file.
     */
    public FingerprintResult computeFingerprint(File modelFile, ModelMetadata metadata) throws IOException {
        FingerprintResult res = new FingerprintResult();
        res.sha256 = HashUtil.sha256(modelFile);
        res.fileSizeBytes = modelFile.length();

        // Structural fingerprint combines inputs, outputs, and format
        String inputsStr = metadata != null ? metadata.inputTensors.toString() : "none";
        String outputsStr = metadata != null ? metadata.outputTensors.toString() : "none";
        String rawStructural = String.format("%s:%d:%s:%s:%s",
                res.sha256, res.fileSizeBytes,
                metadata != null ? metadata.format : "UNKNOWN",
                inputsStr, outputsStr);

        res.structuralFingerprint = HashUtil.sha256(rawStructural);
        res.identityMatches = true;
        res.statusDescription = "Model identity and structural fingerprint computed successfully.";
        return res;
    }

    /**
     * Validates whether a model file's current hash matches its registered database fingerprint.
     */
    public boolean verifyIdentity(MlModel registeredModel, File modelFile) {
        if (registeredModel.getSha256Hash() == null) {
            return false;
        }
        try {
            String currentHash = HashUtil.sha256(modelFile);
            return currentHash.equalsIgnoreCase(registeredModel.getSha256Hash());
        } catch (IOException e) {
            return false;
        }
    }
}
