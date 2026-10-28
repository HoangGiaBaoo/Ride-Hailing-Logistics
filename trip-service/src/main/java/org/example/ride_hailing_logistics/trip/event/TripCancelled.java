package org.example.ride_hailing_logistics.trip.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TripCancelled(UUID tripId, String tripCode, UUID customerId, UUID driverId, String serviceType,
                            String paymentMethod, String actor, String reason, BigDecimal feeAmount, String currency,
                            String policyVersion, Instant cancelledAt) {}
