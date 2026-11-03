package com.sentinelcv.exception;

/**
 * Exception for model loading and inspection errors.
 */
public class ModelLoadException extends SentinelCvException {

    public ModelLoadException(String message) {
        super("MODEL_LOAD_ERROR", message);
    }

    public ModelLoadException(String message, Throwable cause) {
        super("MODEL_LOAD_ERROR", message, cause);
    }
}
