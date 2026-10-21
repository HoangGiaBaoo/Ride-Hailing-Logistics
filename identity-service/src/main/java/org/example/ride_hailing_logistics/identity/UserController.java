package org.example.ride_hailing_logistics.identity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.identity.IdentityDtos.UpdateProfileRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.UpdateRolesRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.UpdateStatusRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.UserResponse;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/v1/users/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> me() {
        return ApiResponse.ok(userService.me(CurrentUser.id()));
    }

    @PatchMapping("/api/v1/users/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateMe(CurrentUser.id(), request.fullName()));
    }

    @GetMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<UserResponse>> search(@RequestParam(required = false) Role role,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.search(role, q, PageResponse.pageable(page, size, Sort.by("createdAt").descending())));
    }

    @PutMapping("/api/v1/admin/users/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request) {
        return ApiResponse.ok(userService.updateRoles(id, request.roles(), request.reason(), CurrentUser.actor()));
    }

    @PutMapping("/api/v1/admin/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.ok(userService.updateStatus(id, request.status(), request.reason(), CurrentUser.actor()));
    }
}
