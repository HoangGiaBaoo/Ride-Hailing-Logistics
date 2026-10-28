package org.example.ride_hailing_logistics.trip.controller;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.trip.dto.response.AvailabilityResponse;
import org.example.ride_hailing_logistics.trip.service.DriverStateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Online/Offline cua tai xe (FR-DRV-010, FR-DRV-011). driverId luon lay tu token. */
@RestController
@RequestMapping("/api/v1/drivers/me")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverAvailabilityController {

    private final DriverStateService driverStates;

    @GetMapping("/availability")
    public ApiResponse<AvailabilityResponse> availability() {
        return ApiResponse.ok(driverStates.status(CurrentUser.id()));
    }

    @PostMapping("/online")
    public ApiResponse<AvailabilityResponse> online() {
        return ApiResponse.ok(driverStates.goOnline(CurrentUser.id()));
    }

    @PostMapping("/offline")
    public ApiResponse<AvailabilityResponse> offline() {
        return ApiResponse.ok(driverStates.goOffline(CurrentUser.id()));
    }
}
