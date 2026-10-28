package org.example.ride_hailing_logistics.trip.event;

import java.time.Instant;
import java.util.UUID;

public record TripAccepted(UUID tripId, UUID driverId, Instant acceptedAt) {}
