package org.example.ride_hailing_logistics.driver.dto.response;

import org.example.ride_hailing_logistics.driver.enums.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record DriverSummaryResponse(UUID driverId, String fullName, ReviewStatus reviewStatus, Instant submittedAt) {}
