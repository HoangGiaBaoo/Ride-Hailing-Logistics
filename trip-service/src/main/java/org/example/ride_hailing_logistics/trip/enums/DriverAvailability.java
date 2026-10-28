package org.example.ride_hailing_logistics.trip.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Trang thai kha dung cua tai xe (SRS §7.2). Chi trip-service doi gia tri nay, luon bang cap nhat co dieu kien. */
@Getter
@RequiredArgsConstructor
public enum DriverAvailability {
    OFFLINE("Ngoại tuyến"),
    AVAILABLE("Sẵn sàng nhận chuyến"),
    OFFERED("Đang có đề nghị"),
    BUSY("Đang thực hiện chuyến");

    private final String displayName;
}
