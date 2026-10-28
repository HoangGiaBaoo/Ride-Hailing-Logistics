package org.example.ride_hailing_logistics.trip.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

/** Offer da het han / bi dong, chuyen da co tai xe, hoac tai xe khong con o trang thai nhan chuyen (FR-MAT-012). */
public class OfferUnavailableException extends BusinessException {

    public OfferUnavailableException(ErrorCode code, String message) {
        super(code, message);
    }

    public static OfferUnavailableException expired() {
        return new OfferUnavailableException(ErrorCode.OFFER_EXPIRED, "Đề nghị chuyến đã hết hạn hoặc không còn hiệu lực.");
    }

    public static OfferUnavailableException alreadyAssigned(String message) {
        return new OfferUnavailableException(ErrorCode.DRIVER_ALREADY_ASSIGNED, message);
    }
}
