package org.example.ride_hailing_logistics.trip.event.local;

import java.util.UUID;

public record OfferClosed(UUID offerId, UUID tripId, UUID driverId, String reason) {}
