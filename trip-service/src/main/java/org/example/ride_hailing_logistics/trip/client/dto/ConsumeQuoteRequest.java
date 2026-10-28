package org.example.ride_hailing_logistics.trip.client.dto;

import java.util.UUID;

/** tripId xac dinh tu Idempotency-Key: goi lai cung tripId thi pricing tra cung snapshot (BR-015). */
public record ConsumeQuoteRequest(UUID customerId, UUID tripId) {}
