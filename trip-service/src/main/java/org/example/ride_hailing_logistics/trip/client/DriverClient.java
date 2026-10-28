package org.example.ride_hailing_logistics.trip.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.trip.client.dto.OnlineEligibilityResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "driver-service", contextId = "tripDriverClient", fallbackFactory = DriverClientFallbackFactory.class,
        primary = false)
public interface DriverClient {

    @GetMapping("/internal/drivers/{driverId}/online-eligibility")
    ApiResponse<OnlineEligibilityResponse> onlineEligibility(@PathVariable("driverId") UUID driverId);
}
