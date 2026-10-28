package org.example.ride_hailing_logistics.driver.dto.response;

import org.example.ride_hailing_logistics.driver.enums.ReviewDecisionType;

import java.time.Instant;
import java.util.UUID;

public record DecisionResponse(ReviewDecisionType decision, String reason, UUID reviewerId, int profileVersion,
                               Instant createdAt) {}
