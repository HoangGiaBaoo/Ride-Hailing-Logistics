package org.example.ride_hailing_logistics.identity.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

public class UserNotFoundException extends BusinessException {

    public UserNotFoundException() {
        super(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài khoản.");
    }
}
