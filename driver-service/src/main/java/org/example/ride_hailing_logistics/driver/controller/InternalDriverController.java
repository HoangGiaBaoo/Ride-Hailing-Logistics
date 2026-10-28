package org.example.ride_hailing_logistics.driver.controller;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.driver.dto.response.OnlineEligibilityResponse;
import org.example.ride_hailing_logistics.driver.service.DriverProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API noi bo cho trip-service (chi token SERVICE, gateway khong dinh tuyen /internal). */
@RestController
@RequestMapping("/internal/drivers")
@RequiredArgsConstructor
public class InternalDriverController {

    private final DriverProfileService profileService;

    @GetMapping("/{driverId}/online-eligibility")
    public ApiResponse<OnlineEligibilityResponse> onlineEligibility(@PathVariable UUID driverId) {
        return ApiResponse.ok(profileService.onlineEligibility(driverId));
    }
}
