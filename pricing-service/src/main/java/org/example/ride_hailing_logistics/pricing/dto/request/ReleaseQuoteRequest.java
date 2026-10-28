package org.example.ride_hailing_logistics.pricing.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReleaseQuoteRequest(@NotNull UUID tripId) {}
