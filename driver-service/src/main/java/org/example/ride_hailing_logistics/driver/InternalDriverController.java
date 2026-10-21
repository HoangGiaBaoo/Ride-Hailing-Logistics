package org.example.ride_hailing_logistics.driver;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.driver.DriverDtos.DriverSnapshot;
import org.example.ride_hailing_logistics.location.LocationService;
import org.example.ride_hailing_logistics.location.Telemetry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * API noi bo cua driver-service cho trip-service (dispatch) va pricing-service (surge).
 * Chi token SERVICE goi duoc; gateway khong dinh tuyen /internal.
 */
@RestController
@RequestMapping("/internal/drivers")
@RequiredArgsConstructor
public class InternalDriverController {

    private final DriverAvailabilityService availability;
    private final LocationService locationService;

    /**
     * Ung vien gan diem don: vi tri moi + du chat luong (location) VA dang AVAILABLE, ho so duyet, dung loai xe (driver).
     * Gop hai buoc loc trong mot loi goi de trip-service khong phai goi hai lan (FR-MAT-002, FR-LOC-009).
     */
    @GetMapping("/candidates")
    public ApiResponse<List<Candidate>> candidates(@RequestParam double lat, @RequestParam double lng,
                                                  @RequestParam double radiusMeters, @RequestParam VehicleType vehicleType,
                                                  @RequestParam int limit) {
        List<LocationService.NearbyDriver> nearby = locationService.findNearby(lat, lng, radiusMeters, vehicleType, limit);
        if (nearby.isEmpty()) return ApiResponse.ok(List.of());
        Map<UUID, LocationService.NearbyDriver> byId = nearby.stream()
                .collect(Collectors.toMap(LocationService.NearbyDriver::driverId, Function.identity(), (a, b) -> a));
        Set<UUID> eligible = availability.filterEligible(byId.keySet(), vehicleType).stream()
                .map(EligibleDriverRow::driverId).collect(Collectors.toSet());
        return ApiResponse.ok(eligible.stream().map(byId::get)
                .map(n -> new Candidate(n.driverId(), n.distanceMeters(), n.locationAgeMillis()))
                .toList());
    }

    /** Nguon cung cho surge: tai xe AVAILABLE co vi tri moi trong o geohash (FR-PRI-008). */
    @GetMapping("/supply")
    public ApiResponse<Long> supply(@RequestParam String cell) {
        return ApiResponse.ok(availability.countAvailableAmong(locationService.freshDriversInCell(cell)));
    }

    @GetMapping("/{driverId}/snapshot")
    public ApiResponse<DriverSnapshot> snapshot(@PathVariable UUID driverId) {
        return ApiResponse.ok(availability.snapshot(driverId));
    }

    @GetMapping("/{driverId}/location")
    public ApiResponse<Position> location(@PathVariable UUID driverId) {
        return ApiResponse.ok(locationService.latest(driverId)
                .map(l -> new Position(l.latitude(), l.longitude()))
                .orElseThrow(() -> BusinessException.notFound("vị trí tài xế")));
    }

    /** Telemetry tai xe gui qua WebSocket cua trip-service; driverId da lay tu phien STOMP da xac thuc. */
    @PostMapping("/{driverId}/location")
    public ApiResponse<LocationService.TelemetryAck> ingest(@PathVariable UUID driverId, @Valid @RequestBody Telemetry telemetry) {
        return ApiResponse.ok(locationService.ingest(driverId, telemetry));
    }

    @PostMapping("/{driverId}/reserve")
    public ApiResponse<HoldResult> reserve(@PathVariable UUID driverId, @Valid @RequestBody HoldCommand cmd) {
        return ApiResponse.ok(new HoldResult(availability.reserve(driverId, cmd.offerId())));
    }

    @PostMapping("/{driverId}/release-offer")
    public ApiResponse<HoldResult> releaseOffer(@PathVariable UUID driverId, @Valid @RequestBody HoldCommand cmd) {
        return ApiResponse.ok(new HoldResult(availability.releaseOffer(driverId, cmd.offerId())));
    }

    @PostMapping("/{driverId}/busy")
    public ApiResponse<HoldResult> markBusy(@PathVariable UUID driverId, @Valid @RequestBody TripHoldCommand cmd) {
        return ApiResponse.ok(new HoldResult(availability.markBusy(driverId, cmd.offerId(), cmd.tripId())));
    }

    @PostMapping("/{driverId}/revert-busy")
    public ApiResponse<HoldResult> revertBusy(@PathVariable UUID driverId, @Valid @RequestBody TripHoldCommand cmd) {
        return ApiResponse.ok(new HoldResult(availability.revertBusy(driverId, cmd.tripId(), cmd.offerId())));
    }

    @PostMapping("/{driverId}/finish-trip")
    public ApiResponse<HoldResult> finishTrip(@PathVariable UUID driverId, @Valid @RequestBody TripHoldCommand cmd) {
        return ApiResponse.ok(new HoldResult(availability.finishTrip(driverId, cmd.tripId())));
    }

    public record Candidate(UUID driverId, double distanceMeters, long locationAgeMillis) {}

    public record Position(double latitude, double longitude) {}

    public record HoldCommand(@NotNull UUID offerId) {}

    public record TripHoldCommand(UUID offerId, @NotNull UUID tripId) {}

    public record HoldResult(boolean success) {}
}
