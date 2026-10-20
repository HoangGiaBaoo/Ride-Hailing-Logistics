package org.example.ride_hailing_logistics.common.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum VehicleType {
    MOTORBIKE("Xe máy", Set.of(ServiceType.RIDE, ServiceType.DELIVERY)),
    CAR_4("Ô tô 4 chỗ", Set.of(ServiceType.RIDE)),
    CAR_7("Ô tô 7 chỗ", Set.of(ServiceType.RIDE)),
    VAN("Xe tải van", Set.of(ServiceType.DELIVERY));

    private final String displayName;
    private final Set<ServiceType> supportedServices;

    public boolean supports(ServiceType serviceType) {
        return supportedServices.contains(serviceType);
    }
}
