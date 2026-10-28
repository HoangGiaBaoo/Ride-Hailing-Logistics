package org.example.ride_hailing_logistics.trip.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

/** FR-TRIP-007: moi khach chi mot chuyen chua ket thuc. */
public class ActiveTripExistsException extends BusinessException {

    public ActiveTripExistsException() {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, "Bạn đang có một chuyến chưa kết thúc.");
    }
}
