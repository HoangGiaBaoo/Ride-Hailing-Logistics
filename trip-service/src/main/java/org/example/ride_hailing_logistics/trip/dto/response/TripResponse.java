package org.example.ride_hailing_logistics.trip.dto.response;

import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.trip.enums.CancelReason;
import org.example.ride_hailing_logistics.trip.enums.TripActorRole;
import org.example.ride_hailing_logistics.trip.enums.TripStatus;
import java.time.Instant;
import java.util.UUID;

/** Du lieu da duoc cat theo nguoi xem trong TripMapper.toResponse (BR-013). */
public record TripResponse(UUID id, String code, TripStatus status, String statusDisplay, ServiceType serviceType,
                           VehicleType vehicleType, PlaceResponse pickup, PlaceResponse dropoff, int distanceMeters,
                           int durationSeconds, FareResponse fare, PaymentMethod paymentMethod, DriverInfoResponse driver,
                           DeliveryResponse delivery, String startPin, String deliveryCode, CancelReason cancelReason,
                           TripActorRole cancelledBy, Instant createdAt, Instant acceptedAt, Instant arrivedAt,
                           Instant startedAt, Instant completedAt, Instant endedAt, long version) {}
