package org.example.ride_hailing_logistics.trip.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

/** Chuyen khong ton tai hoac nguoi goi khong lien quan: cung mot loi 404 de khong lo chuyen ton tai (FR-IAM-010). */
public class TripNotFoundException extends BusinessException {

    public TripNotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy chuyến.");
    }
}
