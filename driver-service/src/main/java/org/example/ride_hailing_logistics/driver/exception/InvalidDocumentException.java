package org.example.ride_hailing_logistics.driver.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

/** Tep tai len sai dinh dang / qua lon / rong (NFR-SEC-011, DR-VAL-004). */
public class InvalidDocumentException extends BusinessException {

    public InvalidDocumentException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
