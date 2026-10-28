package org.example.ride_hailing_logistics.trip.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.trip.client.dto.ConsumeQuoteRequest;
import org.example.ride_hailing_logistics.trip.client.dto.QuoteSnapshotResponse;
import org.example.ride_hailing_logistics.trip.client.dto.ReleaseQuoteRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

/** POST khong duoc thu lai tu dong (GetOnlyRetryer); ca hai thao tac idempotent theo tripId nen goi lai tay an toan. */
@FeignClient(name = "pricing-service", contextId = "tripPricingClient", fallbackFactory = PricingClientFallbackFactory.class,
        primary = false)
public interface PricingClient {

    @PostMapping("/internal/quotes/{quoteId}/consume")
    ApiResponse<QuoteSnapshotResponse> consume(@PathVariable("quoteId") UUID quoteId, @RequestBody ConsumeQuoteRequest request);

    @PostMapping("/internal/quotes/{quoteId}/release")
    ApiResponse<Void> release(@PathVariable("quoteId") UUID quoteId, @RequestBody ReleaseQuoteRequest request);
}
