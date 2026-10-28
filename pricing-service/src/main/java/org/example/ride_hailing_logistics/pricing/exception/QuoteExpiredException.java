package org.example.ride_hailing_logistics.pricing.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

public class QuoteExpiredException extends BusinessException {

    public QuoteExpiredException(String message) {
        super(ErrorCode.QUOTE_EXPIRED, message);
    }
}
