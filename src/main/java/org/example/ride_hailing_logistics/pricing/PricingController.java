package org.example.ride_hailing_logistics.pricing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.DeactivateRequest;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.PricingRuleRequest;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.PricingRuleResponse;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteRequest;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PricingController {

    private final QuoteService quoteService;
    private final PricingRuleService ruleService;

    @PostMapping("/api/v1/quotes")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<QuoteResponse> quote(@Valid @RequestBody QuoteRequest request) {
        return ApiResponse.ok(quoteService.createQuote(CurrentUser.id(), request));
    }

    @GetMapping("/api/v1/admin/pricing-rules")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_STAFF')")
    public ApiResponse<List<PricingRuleResponse>> rules() {
        return ApiResponse.ok(ruleService.list());
    }

    @PostMapping("/api/v1/admin/pricing-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PricingRuleResponse> create(@Valid @RequestBody PricingRuleRequest request) {
        return ApiResponse.ok(ruleService.create(request, CurrentUser.actor()));
    }

    @PostMapping("/api/v1/admin/pricing-rules/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PricingRuleResponse> deactivate(@PathVariable UUID id, @Valid @RequestBody DeactivateRequest request) {
        return ApiResponse.ok(ruleService.deactivate(id, request.reason(), CurrentUser.actor()));
    }
}
