package org.example.ride_hailing_logistics.common.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** RBAC toi thieu theo FR-IAM-009. Dat o common vi moi mien deu kiem quyen theo vai tro. */
@Getter
@RequiredArgsConstructor
public enum Role {
    CUSTOMER("Khách hàng"),
    DRIVER("Tài xế"),
    REVIEWER("Nhân viên kiểm duyệt"),
    SUPPORT_STAFF("Nhân viên hỗ trợ"),
    FINANCE_STAFF("Nhân viên tài chính"),
    ADMIN("Quản trị viên");

    private final String displayName;
}
