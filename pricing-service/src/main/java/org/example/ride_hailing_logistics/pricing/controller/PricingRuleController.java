package org.example.ride_hailing_logistics.pricing.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.pricing.dto.request.DeactivateRequest;
import org.example.ride_hailing_logistics.pricing.dto.request.PricingRuleRequest;
import org.example.ride_hailing_logistics.pricing.dto.response.PricingRuleResponse;
import org.example.ride_hailing_logistics.pricing.service.PricingRuleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/pricing-rules")
@RequiredArgsConstructor
public class PricingRuleController {

    private final PricingRuleService ruleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_STAFF')")
    public ApiResponse<List<PricingRuleResponse>> rules() {
        return ApiResponse.ok(ruleService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PricingRuleResponse> create(@Valid @RequestBody PricingRuleRequest request) {
        return ApiResponse.ok(ruleService.create(request, CurrentUser.actor()));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PricingRuleResponse> deactivate(@PathVariable UUID id, @Valid @RequestBody DeactivateRequest request) {
        return ApiResponse.ok(ruleService.deactivate(id, request.reason(), CurrentUser.actor()));
    }
}
