package org.example.ride_hailing_logistics.pricing.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

/** Quote cua nguoi khac cung tra 404: khong lo quote co ton tai (FR-IAM-010). */
public class QuoteNotFoundException extends BusinessException {

    public QuoteNotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy báo giá.");
    }
}
