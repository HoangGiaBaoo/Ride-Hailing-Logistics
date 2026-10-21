package org.example.ride_hailing_logistics.trip.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

// primary = false: integration test thay bang ban gia @Primary (Feign mac dinh tu danh dau primary)
@FeignClient(name = "pricing-service", contextId = "tripPricingClient", primary = false)
public interface PricingClient {

    /** Dung quote cho chuyen tripId; goi lai cung tripId tra cung ket qua (an toan khi retry). */
    @PostMapping("/internal/quotes/{quoteId}/consume")
    ApiResponse<QuoteSnapshot> consume(@PathVariable("quoteId") UUID quoteId, @RequestBody ConsumeCommand cmd);

    @PostMapping("/internal/quotes/{quoteId}/release")
    ApiResponse<Object> release(@PathVariable("quoteId") UUID quoteId, @RequestBody ReleaseCommand cmd);

    record ConsumeCommand(UUID customerId, UUID tripId) {}

    record ReleaseCommand(UUID tripId) {}
}
