package org.example.ride_hailing_logistics.trip.client;

import org.example.ride_hailing_logistics.common.client.RemoteFallbacks;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** driver-service khong tra loi: khong cho Online (khong kiem duoc giay to/xe), bao loi tam thoi. */
@Component
public class DriverClientFallbackFactory implements FallbackFactory<DriverClient> {

    @Override
    public DriverClient create(Throwable cause) {
        return driverId -> {
            throw RemoteFallbacks.translate("driver-service", cause);
        };
    }
}
