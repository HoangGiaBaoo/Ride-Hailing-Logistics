package org.example.ride_hailing_logistics.location.service;

import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.location.dto.request.TelemetryRequest;
import org.example.ride_hailing_logistics.location.dto.response.LatestLocationResponse;
import org.example.ride_hailing_logistics.location.dto.response.NearbyDriverResponse;
import org.example.ride_hailing_logistics.location.dto.response.SupplyResponse;
import org.example.ride_hailing_logistics.location.dto.response.TelemetryAckResponse;
import org.example.ride_hailing_logistics.location.event.DriverAvailabilityChanged;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationService {

    /** driverId luon lay tu phien da xac thuc, khong tu payload (FR-LOC-005). */
    TelemetryAckResponse ingest(UUID driverId, TelemetryRequest telemetry);

    /** Ung vien gan diem don, da loc do moi va chat luong vi tri (FR-LOC-009, FR-LOC-010, BR-004). */
    List<NearbyDriverResponse> findNearby(double lat, double lng, double radiusMeters, VehicleType vehicleType, int limit);

    SupplyResponse supply(String cell);

    Optional<LatestLocationResponse> latest(UUID driverId);

    void applyAvailability(DriverAvailabilityChanged change);
}
