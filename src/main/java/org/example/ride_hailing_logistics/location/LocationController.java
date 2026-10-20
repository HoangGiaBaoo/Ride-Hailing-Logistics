package org.example.ride_hailing_logistics.location;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Kenh REST du phong cho telemetry; kenh chinh la WebSocket /app/driver/location. */
@RestController
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @PostMapping("/api/v1/drivers/me/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<LocationService.TelemetryAck> report(@Valid @RequestBody Telemetry telemetry) {
        return ApiResponse.ok(locationService.ingest(CurrentUser.id(), telemetry));
    }
}
