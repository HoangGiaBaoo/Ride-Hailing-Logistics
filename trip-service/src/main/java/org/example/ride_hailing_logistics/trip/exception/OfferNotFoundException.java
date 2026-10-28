package org.example.ride_hailing_logistics.trip.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

/** Offer cua tai xe khac cung tra 404 (UC-06). */
public class OfferNotFoundException extends BusinessException {

    public OfferNotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đề nghị chuyến.");
    }
}
