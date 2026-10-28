package org.example.ride_hailing_logistics.pricing.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** customerId lay tu token o trip-service, khong tu client; tripId de goi lai (retry) idempotent. */
public record ConsumeQuoteRequest(@NotNull UUID customerId, @NotNull UUID tripId) {}
