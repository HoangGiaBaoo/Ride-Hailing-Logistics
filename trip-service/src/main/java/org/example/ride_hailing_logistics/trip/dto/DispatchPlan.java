package org.example.ride_hailing_logistics.trip.dto;

import org.example.ride_hailing_logistics.common.domain.VehicleType;

import java.util.UUID;

/** Dau vao cho mot vong tim tai xe, chot duoi khoa hang chuyen roi moi goi location-service ngoai transaction. */
public record DispatchPlan(UUID tripId, double pickupLat, double pickupLng, VehicleType vehicleType, int round,
                           double radiusMeters) {}
