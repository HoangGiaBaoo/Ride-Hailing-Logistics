package org.example.ride_hailing_logistics.trip;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

/** Trang thai chuyen (SRS §4.6.1). */
@Getter
@RequiredArgsConstructor
public enum TripStatus {
    CREATED("Đã tạo"),
    MATCHING("Đang tìm tài xế"),
    ACCEPTED("Đã có tài xế"),
    PICKING_UP("Tài xế đang đến"),
    ARRIVED("Tài xế đã đến"),
    IN_TRIP("Đang di chuyển"),
    COMPLETED("Hoàn tất"),
    CANCELLED("Đã hủy"),
    NO_DRIVER("Không tìm được tài xế");

    /** Nhom trang thai co tai xe dang gan — trung voi partial unique index uq_trip_active_driver. */
    public static final Set<TripStatus> DRIVER_ACTIVE = EnumSet.of(ACCEPTED, PICKING_UP, ARRIVED, IN_TRIP);

    /** Nhom trang thai hoat dong cua khach — trung voi uq_trip_active_customer. */
    public static final Set<TripStatus> CUSTOMER_ACTIVE = EnumSet.of(CREATED, MATCHING, ACCEPTED, PICKING_UP, ARRIVED, IN_TRIP);

    public static final Set<TripStatus> TERMINAL = EnumSet.of(COMPLETED, CANCELLED, NO_DRIVER);

    private final String displayName;

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
