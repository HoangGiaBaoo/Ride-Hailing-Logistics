package org.example.ride_hailing_logistics.trip.dto.response;

import java.math.BigDecimal;

public record CancellationPreviewResponse(boolean allowed, BigDecimal fee, String currency, String policyVersion, String message) {}
