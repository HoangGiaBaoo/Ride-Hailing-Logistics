package org.example.ride_hailing_logistics.common.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Tu vung dung chung giua cac mien (shared kernel): driver, pricing, trip, payment, wallet. */
@Getter
@RequiredArgsConstructor
public enum ServiceType {
    RIDE("Chở khách"),
    DELIVERY("Giao hàng");

    private final String displayName;
}
