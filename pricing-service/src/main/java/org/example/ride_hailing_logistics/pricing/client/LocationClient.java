package org.example.ride_hailing_logistics.pricing.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Nguon cung cho surge tu location-service (Eureka: ten service thay dia chi; circuit breaker + fallback). */
@FeignClient(name = "location-service", contextId = "pricingLocationClient",
        fallbackFactory = LocationClientFallbackFactory.class,
        primary = false)
public interface LocationClient {

    @GetMapping("/internal/locations/supply")
    ApiResponse<SupplyView> supply(@RequestParam("cell") String cell);

    record SupplyView(String cell, long availableDrivers) {}
}
