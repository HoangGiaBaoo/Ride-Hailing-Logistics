package org.example.ride_hailing_logistics.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.common.security.Role;
import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<Role> roles, @NotBlank @Size(max = 500) String reason) {}
