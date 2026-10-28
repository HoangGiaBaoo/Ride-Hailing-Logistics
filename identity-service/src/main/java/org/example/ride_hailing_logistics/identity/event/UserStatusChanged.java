package org.example.ride_hailing_logistics.identity.event;

import org.example.ride_hailing_logistics.identity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

/** Tai khoan bi khoa: trip-service dua tai xe dang ranh ve Offline (BR-001). */
public record UserStatusChanged(UUID userId, UserStatus status, Instant changedAt) {}
