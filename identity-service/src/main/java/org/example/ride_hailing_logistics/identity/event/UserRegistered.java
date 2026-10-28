package org.example.ride_hailing_logistics.identity.event;

import org.example.ride_hailing_logistics.common.security.Role;

import java.time.Instant;
import java.util.UUID;

/** driver-service mo ho so nhap cho tai xe; ten + SDT de service khac chup lai, khoi goi nguoc identity. */
public record UserRegistered(UUID userId, Role role, String fullName, String phone, Instant registeredAt) {}
