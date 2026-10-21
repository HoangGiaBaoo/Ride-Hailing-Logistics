package org.example.ride_hailing_logistics.driver.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "trip-service", contextId = "driverTripClient")
public interface TripClient {

    /** Offer PENDING va chuyen dang chay cua tai xe theo trip-service (nguon su that ve viec gan chuyen). */
    @GetMapping("/internal/drivers/{driverId}/engagement")
    ApiResponse<Engagement> engagement(@PathVariable("driverId") UUID driverId);

    record Engagement(UUID pendingOfferId, UUID activeTripId) {}
}
