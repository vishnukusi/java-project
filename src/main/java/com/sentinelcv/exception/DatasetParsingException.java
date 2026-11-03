package com.sentinelcv.exception;

/**
 * Exception for dataset parsing and validation errors.
 */
public class DatasetParsingException extends SentinelCvException {

    public DatasetParsingException(String message) {
        super("DATASET_PARSE_ERROR", message);
    }

    public DatasetParsingException(String message, Throwable cause) {
        super("DATASET_PARSE_ERROR", message, cause);
    }
}
