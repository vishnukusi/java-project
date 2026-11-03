package com.sentinelcv.enums;

/**
 * Explicit execution status for mandatory security and integrity checks.
 * Prevents fragile inference of check availability from severity strings or descriptions.
 */
public enum CheckExecutionStatus {
    PASSED,
    FAILED,
    UNAVAILABLE,
    NOT_EXECUTED
}
