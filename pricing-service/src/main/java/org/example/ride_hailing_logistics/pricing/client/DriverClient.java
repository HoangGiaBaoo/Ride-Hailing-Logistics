package org.example.ride_hailing_logistics.pricing.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "driver-service", contextId = "pricingDriverClient")
public interface DriverClient {

    /** So tai xe AVAILABLE co vi tri moi trong o geohash (nguon cung cho surge, FR-PRI-008). */
    @GetMapping("/internal/drivers/supply")
    ApiResponse<Long> supply(@RequestParam("cell") String cell);
}
