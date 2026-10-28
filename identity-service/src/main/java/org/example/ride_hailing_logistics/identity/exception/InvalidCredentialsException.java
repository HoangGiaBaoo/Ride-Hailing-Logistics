package org.example.ride_hailing_logistics.identity.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

/** Mot thong bao cho moi kieu that bai dang nhap: khong lo tai khoan co ton tai / bi khoa (FR-IAM-006). */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException() {
        super(ErrorCode.AUTHENTICATION_REQUIRED,
                "Thông tin đăng nhập không đúng hoặc tài khoản tạm thời không thể đăng nhập.");
    }
}
