package com.virtualrift.common.model;

/**
 * scan status with state transition validation.
 *
 * valid transitions:
 * - PENDING → RUNNING, CANCELLED
 * - RUNNING → COMPLETED, FAILED, CANCELLED
 *
 * final states: COMPLETED, FAILED, CANCELLED
 */
public enum ScanStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(ScanStatus target) {
        if (this == target) {
            return false;
        }

        return switch (this) {
            case PENDING -> target == RUNNING || target == CANCELLED;
            case RUNNING -> target == COMPLETED || target == FAILED || target == CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false; // Final states
        };
    }

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean isInProgress() {
        return this == PENDING || this == RUNNING;
    }

    public static ScanStatus fromString(String status) {
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        try {
            return ScanStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid scan status: " + status, e);
        }
    }
}
