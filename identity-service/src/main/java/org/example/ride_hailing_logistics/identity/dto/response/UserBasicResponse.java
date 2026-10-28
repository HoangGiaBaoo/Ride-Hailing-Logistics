package org.example.ride_hailing_logistics.identity.dto.response;

import org.example.ride_hailing_logistics.common.security.Role;

import java.util.Set;
import java.util.UUID;

/** Thong tin co ban cho service khac qua /internal: khong co email, password hash. */
public record UserBasicResponse(UUID id, String fullName, String phone, boolean active, Set<Role> roles) {}
