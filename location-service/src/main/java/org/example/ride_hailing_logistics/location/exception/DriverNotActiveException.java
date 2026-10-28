package org.example.ride_hailing_logistics.location.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

public class DriverNotActiveException extends BusinessException {

    public DriverNotActiveException() {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, "Bạn đang Offline, hãy bật Online trước khi gửi vị trí.");
    }
}
