package org.example.ride_hailing_logistics.driver.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.driver.enums.ReviewDecisionType;

public record ReviewDecisionRequest(@NotNull ReviewDecisionType decision, @Size(max = 500) String reason) {}
