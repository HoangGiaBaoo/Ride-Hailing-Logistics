package org.example.ride_hailing_logistics.payment.dto.response;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CommissionRuleResponse(UUID id, ServiceType serviceType, BigDecimal rate, int version,
                                     Instant effectiveFrom, Instant effectiveTo) {}
