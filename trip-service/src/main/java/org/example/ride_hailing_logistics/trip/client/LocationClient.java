package org.example.ride_hailing_logistics.trip.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.trip.client.dto.DriverLocationResponse;
import org.example.ride_hailing_logistics.trip.client.dto.NearbyDriverResponse;
import org.example.ride_hailing_logistics.trip.client.dto.TelemetryAckResponse;
import org.example.ride_hailing_logistics.trip.dto.request.TelemetryRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "location-service", contextId = "tripLocationClient", fallbackFactory = LocationClientFallbackFactory.class,
        primary = false)
public interface LocationClient {

    @GetMapping("/internal/locations/nearby")
    ApiResponse<List<NearbyDriverResponse>> nearby(@RequestParam("lat") double lat, @RequestParam("lng") double lng,
                                                   @RequestParam("radiusMeters") double radiusMeters,
                                                   @RequestParam("vehicleType") VehicleType vehicleType,
                                                   @RequestParam("limit") int limit);

    @GetMapping("/internal/locations/drivers/{driverId}")
    ApiResponse<DriverLocationResponse> latest(@PathVariable("driverId") UUID driverId);

    @PostMapping("/internal/locations/drivers/{driverId}")
    ApiResponse<TelemetryAckResponse> ingest(@PathVariable("driverId") UUID driverId, @RequestBody TelemetryRequest telemetry);
}
