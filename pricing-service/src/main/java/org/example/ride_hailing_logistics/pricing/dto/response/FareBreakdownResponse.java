package org.example.ride_hailing_logistics.pricing.dto.response;

import java.math.BigDecimal;

public record FareBreakdownResponse(BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare,
                                    BigDecimal bookingFee, BigDecimal surgeAmount, BigDecimal minimumFare,
                                    boolean minimumApplied) {}
