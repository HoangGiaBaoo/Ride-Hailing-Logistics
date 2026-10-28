package org.example.ride_hailing_logistics.trip.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Ma ly do huy bat buoc (FR-CAN-002). OTHER bat buoc kem mo ta. */
@Getter
@RequiredArgsConstructor
public enum CancelReason {
    CHANGED_PLANS("Đổi kế hoạch"),
    WAIT_TOO_LONG("Chờ quá lâu"),
    DRIVER_TOO_FAR("Tài xế ở quá xa"),
    CUSTOMER_NO_SHOW("Khách không xuất hiện"),
    WRONG_PICKUP("Sai điểm đón"),
    VEHICLE_ISSUE("Sự cố phương tiện"),
    SAFETY_CONCERN("Lo ngại an toàn"),
    SUPPORT_EXCEPTION("Hỗ trợ xử lý ngoại lệ"),
    OTHER("Lý do khác");

    private final String displayName;
}
