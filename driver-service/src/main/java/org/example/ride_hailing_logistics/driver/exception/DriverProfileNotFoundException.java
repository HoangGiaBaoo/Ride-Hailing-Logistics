package org.example.ride_hailing_logistics.driver.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

public class DriverProfileNotFoundException extends BusinessException {

    public DriverProfileNotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ tài xế.");
    }
}
