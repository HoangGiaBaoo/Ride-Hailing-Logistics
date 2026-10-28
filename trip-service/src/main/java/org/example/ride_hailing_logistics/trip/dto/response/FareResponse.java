package org.example.ride_hailing_logistics.trip.dto.response;

import java.math.BigDecimal;

public record FareResponse(BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare, BigDecimal bookingFee,
                       BigDecimal surgeMultiplier, BigDecimal quotedTotal, BigDecimal finalFare,
                       BigDecimal cancellationFee, String currency, int pricingRuleVersion) {}
