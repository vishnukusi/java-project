package com.sentinelcv.exception;

/**
 * Base exception for SentinelCV application errors.
 */
public class SentinelCvException extends RuntimeException {

    private final String errorCode;

    public SentinelCvException(String message) {
        super(message);
        this.errorCode = "SENTINEL_ERROR";
    }

    public SentinelCvException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SentinelCvException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "SENTINEL_ERROR";
    }

    public SentinelCvException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
