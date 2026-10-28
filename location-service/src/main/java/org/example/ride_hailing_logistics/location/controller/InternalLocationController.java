package org.example.ride_hailing_logistics.location.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.location.dto.request.TelemetryRequest;
import org.example.ride_hailing_logistics.location.dto.response.LatestLocationResponse;
import org.example.ride_hailing_logistics.location.dto.response.NearbyDriverResponse;
import org.example.ride_hailing_logistics.location.dto.response.SupplyResponse;
import org.example.ride_hailing_logistics.location.dto.response.TelemetryAckResponse;
import org.example.ride_hailing_logistics.location.service.LocationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** API noi bo cho trip-service va pricing-service (chi token SERVICE). */
@Validated
@RestController
@RequestMapping("/internal/locations")
@RequiredArgsConstructor
public class InternalLocationController {

    private final LocationService locationService;

    @GetMapping("/nearby")
    public ApiResponse<List<NearbyDriverResponse>> nearby(@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
                                                          @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
                                                          @RequestParam @Min(1) @Max(50_000) double radiusMeters,
                                                          @RequestParam VehicleType vehicleType,
                                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ApiResponse.ok(locationService.findNearby(lat, lng, radiusMeters, vehicleType, limit));
    }

    @GetMapping("/supply")
    public ApiResponse<SupplyResponse> supply(@RequestParam @Pattern(regexp = "[0-9b-hjkmnp-z]{4,8}") String cell) {
        return ApiResponse.ok(locationService.supply(cell));
    }

    @GetMapping("/drivers/{driverId}")
    public ApiResponse<LatestLocationResponse> latest(@PathVariable UUID driverId) {
        return ApiResponse.ok(locationService.latest(driverId)
                .orElseThrow(() -> BusinessException.notFound("vị trí tài xế")));
    }

    /** Telemetry nhan qua WebSocket o trip-service; driverId da lay tu phien STOMP da xac thuc ben do. */
    @PostMapping("/drivers/{driverId}")
    public ApiResponse<TelemetryAckResponse> ingest(@PathVariable UUID driverId,
                                                    @Valid @RequestBody TelemetryRequest telemetry) {
        return ApiResponse.ok(locationService.ingest(driverId, telemetry));
    }
}
