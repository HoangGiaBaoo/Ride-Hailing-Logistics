package org.example.ride_hailing_logistics.location.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.location.dto.request.TelemetryRequest;
import org.example.ride_hailing_logistics.location.dto.response.TelemetryAckResponse;
import org.example.ride_hailing_logistics.location.service.LocationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Kenh REST cho telemetry; kenh WebSocket /app/driver/location o trip-service chuyen tiep vao /internal. */
@RestController
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @PostMapping("/api/v1/drivers/me/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<TelemetryAckResponse> report(@Valid @RequestBody TelemetryRequest telemetry) {
        return ApiResponse.ok(locationService.ingest(CurrentUser.id(), telemetry));
    }
}
