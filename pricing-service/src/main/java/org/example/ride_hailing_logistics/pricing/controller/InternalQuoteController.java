package org.example.ride_hailing_logistics.pricing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.pricing.dto.request.ConsumeQuoteRequest;
import org.example.ride_hailing_logistics.pricing.dto.request.ReleaseQuoteRequest;
import org.example.ride_hailing_logistics.pricing.dto.response.QuoteSnapshotResponse;
import org.example.ride_hailing_logistics.pricing.service.QuoteService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API noi bo cho trip-service (chi token SERVICE). */
@RestController
@RequestMapping("/internal/quotes")
@RequiredArgsConstructor
public class InternalQuoteController {

    private final QuoteService quoteService;

    @PostMapping("/{quoteId}/consume")
    public ApiResponse<QuoteSnapshotResponse> consume(@PathVariable UUID quoteId,
                                                      @Valid @RequestBody ConsumeQuoteRequest request) {
        return ApiResponse.ok(quoteService.consumeQuote(quoteId, request.customerId(), request.tripId()));
    }

    @PostMapping("/{quoteId}/release")
    public ApiResponse<Void> release(@PathVariable UUID quoteId, @Valid @RequestBody ReleaseQuoteRequest request) {
        quoteService.releaseQuote(quoteId, request.tripId());
        return ApiResponse.ok(null);
    }
}
