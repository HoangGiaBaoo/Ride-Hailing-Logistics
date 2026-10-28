package org.example.ride_hailing_logistics.trip.event;

import java.time.Instant;
import java.util.UUID;

public record TripRequested(UUID tripId, String serviceType, String vehicleType, double pickupLat, double pickupLng,
                            UUID quoteId, Instant requestedAt) {}
