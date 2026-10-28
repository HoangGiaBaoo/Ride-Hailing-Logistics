package org.example.ride_hailing_logistics.pricing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.pricing.dto.request.QuoteRequest;
import org.example.ride_hailing_logistics.pricing.dto.response.QuoteResponse;
import org.example.ride_hailing_logistics.pricing.service.QuoteService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;

    @PostMapping("/api/v1/quotes")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<QuoteResponse> quote(@Valid @RequestBody QuoteRequest request) {
        return ApiResponse.ok(quoteService.createQuote(CurrentUser.id(), request));
    }
}
