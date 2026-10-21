package org.example.ride_hailing_logistics.identity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus {
    ACTIVE("Hoạt động"),
    DISABLED("Đã khóa");

    private final String displayName;
}
