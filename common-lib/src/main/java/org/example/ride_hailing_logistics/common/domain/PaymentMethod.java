package org.example.ride_hailing_logistics.common.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** TBD-09: tien mat va mot phuong thuc dien tu sandbox. */
@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
    CASH("Tiền mặt"),
    SANDBOX_CARD("Thẻ (sandbox)");

    private final String displayName;
}
