package org.example.ride_hailing_logistics.identity.service;

import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.identity.dto.response.UserResponse;
import org.example.ride_hailing_logistics.identity.enums.UserStatus;
import org.springframework.data.domain.Pageable;

import java.util.Set;
import java.util.UUID;

/** Ho so ca nhan va quan tri tai khoan (FR-IAM-008, FR-IAM-009). */
public interface UserService {

    UserResponse me(UUID userId);

    UserResponse updateMe(UUID userId, String fullName);

    PageResponse<UserResponse> search(Role role, String query, Pageable pageable);

    UserResponse updateRoles(UUID userId, Set<Role> roles, String reason, Actor actor);

    UserResponse updateStatus(UUID userId, UserStatus status, String reason, Actor actor);
}
