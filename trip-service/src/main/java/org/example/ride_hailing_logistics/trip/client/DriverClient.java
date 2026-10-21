package org.example.ride_hailing_logistics.trip.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** API noi bo cua driver-service. Dung qua {@link DriverPool}, khong goi thang tu nghiep vu. */
@FeignClient(name = "driver-service", contextId = "tripDriverClient")
public interface DriverClient {

    @GetMapping("/internal/drivers/candidates")
    ApiResponse<List<Candidate>> candidates(@RequestParam("lat") double lat, @RequestParam("lng") double lng,
                                            @RequestParam("radiusMeters") double radiusMeters,
                                            @RequestParam("vehicleType") VehicleType vehicleType,
                                            @RequestParam("limit") int limit);

    @GetMapping("/internal/drivers/{driverId}/snapshot")
    ApiResponse<DriverSnapshot> snapshot(@PathVariable("driverId") UUID driverId);

    @GetMapping("/internal/drivers/{driverId}/location")
    ApiResponse<Position> location(@PathVariable("driverId") UUID driverId);

    @PostMapping("/internal/drivers/{driverId}/location")
    ApiResponse<Map<String, Object>> ingest(@PathVariable("driverId") UUID driverId, @RequestBody Object telemetry);

    @PostMapping("/internal/drivers/{driverId}/reserve")
    ApiResponse<HoldResult> reserve(@PathVariable("driverId") UUID driverId, @RequestBody HoldCommand cmd);

    @PostMapping("/internal/drivers/{driverId}/release-offer")
    ApiResponse<HoldResult> releaseOffer(@PathVariable("driverId") UUID driverId, @RequestBody HoldCommand cmd);

    @PostMapping("/internal/drivers/{driverId}/busy")
    ApiResponse<HoldResult> markBusy(@PathVariable("driverId") UUID driverId, @RequestBody TripHoldCommand cmd);

    @PostMapping("/internal/drivers/{driverId}/revert-busy")
    ApiResponse<HoldResult> revertBusy(@PathVariable("driverId") UUID driverId, @RequestBody TripHoldCommand cmd);

    @PostMapping("/internal/drivers/{driverId}/finish-trip")
    ApiResponse<HoldResult> finishTrip(@PathVariable("driverId") UUID driverId, @RequestBody TripHoldCommand cmd);

    record Candidate(UUID driverId, double distanceMeters, long locationAgeMillis) {}

    record Position(double latitude, double longitude) {}

    record HoldCommand(UUID offerId) {}

    record TripHoldCommand(UUID offerId, UUID tripId) {}

    record HoldResult(boolean success) {}
}
