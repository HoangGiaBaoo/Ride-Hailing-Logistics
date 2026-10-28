package org.example.ride_hailing_logistics.pricing.client;

import org.example.ride_hailing_logistics.common.client.RemoteFallbacks;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** Mach mo / location-service cham: nem loi ro rang, SurgeService bat va dung he so 1.0 (khong doan surge). */
@Component
public class LocationClientFallbackFactory implements FallbackFactory<LocationClient> {

    @Override
    public LocationClient create(Throwable cause) {
        return cell -> {
            throw RemoteFallbacks.translate("location-service", cause);
        };
    }
}
