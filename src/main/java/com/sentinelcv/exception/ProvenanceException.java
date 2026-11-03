package com.sentinelcv.exception;

/**
 * Exception for provenance and integrity verification errors.
 */
public class ProvenanceException extends SentinelCvException {

    public ProvenanceException(String message) {
        super("PROVENANCE_ERROR", message);
    }

    public ProvenanceException(String message, Throwable cause) {
        super("PROVENANCE_ERROR", message, cause);
    }
}
