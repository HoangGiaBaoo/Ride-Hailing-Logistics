package org.example.ride_hailing_logistics.driver.event;

import java.time.Instant;
import java.util.UUID;

/** payment-service mo vi cho tai xe (FR-WAL-001). */
public record DriverApproved(UUID driverId, Instant approvedAt) {}
