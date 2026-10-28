package org.example.ride_hailing_logistics.driver.event;

import java.time.Instant;
import java.util.UUID;

/** trip-service khoa tai xe: dang ranh thi Offline ngay, dang offer/chuyen thi ve Offline khi ket thuc. */
public record DriverSuspended(UUID driverId, String reason, Instant suspendedAt) {}
