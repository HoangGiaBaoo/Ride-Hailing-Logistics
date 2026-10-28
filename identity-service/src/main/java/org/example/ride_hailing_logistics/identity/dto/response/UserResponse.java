package org.example.ride_hailing_logistics.identity.dto.response;

import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.identity.enums.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, String phone, String fullName, UserStatus status, Set<Role> roles,
                           Instant createdAt) {}
