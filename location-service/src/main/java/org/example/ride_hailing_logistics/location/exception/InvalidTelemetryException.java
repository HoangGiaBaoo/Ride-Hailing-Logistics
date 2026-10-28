package org.example.ride_hailing_logistics.location.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

/** Toa do ngoai bien, thoi diem tuong lai/qua cu, accuracy qua lon (FR-LOC-003, FR-LOC-004). */
public class InvalidTelemetryException extends BusinessException {

    public InvalidTelemetryException(String reason) {
        super(ErrorCode.VALIDATION_ERROR, reason);
    }
}
