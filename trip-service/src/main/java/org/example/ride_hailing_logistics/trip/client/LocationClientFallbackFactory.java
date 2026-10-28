package org.example.ride_hailing_logistics.trip.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.client.RemoteFallbacks;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.trip.client.dto.DriverLocationResponse;
import org.example.ride_hailing_logistics.trip.client.dto.NearbyDriverResponse;
import org.example.ride_hailing_logistics.trip.client.dto.TelemetryAckResponse;
import org.example.ride_hailing_logistics.trip.dto.request.TelemetryRequest;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Mach mo: nem loi ro rang de noi goi tu quyet — dispatch hen lai vong sau, kiem "da den diem don" dung toa do
 * trong request. Khong tra danh sach rong gia: "khong co tai xe" va "khong hoi duoc" la hai chuyen khac nhau.
 */
@Component
public class LocationClientFallbackFactory implements FallbackFactory<LocationClient> {

    @Override
    public LocationClient create(Throwable cause) {
        return new LocationClient() {
            @Override
            public ApiResponse<List<NearbyDriverResponse>> nearby(double lat, double lng, double radiusMeters,
                                                                  VehicleType vehicleType, int limit) {
                throw RemoteFallbacks.translate("location-service", cause);
            }

            @Override
            public ApiResponse<DriverLocationResponse> latest(UUID driverId) {
                throw RemoteFallbacks.translate("location-service", cause);
            }

            @Override
            public ApiResponse<TelemetryAckResponse> ingest(UUID driverId, TelemetryRequest telemetry) {
                throw RemoteFallbacks.translate("location-service", cause);
            }
        };
    }
}
