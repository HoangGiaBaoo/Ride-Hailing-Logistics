package org.example.ride_hailing_logistics.identity.controller;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.identity.dto.response.UserBasicResponse;
import org.example.ride_hailing_logistics.identity.service.UserDirectoryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** API noi bo cho service khac (chi token SERVICE, gateway khong dinh tuyen /internal). */
@Validated
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserDirectoryService directory;

    @GetMapping("/{id}")
    public ApiResponse<UserBasicResponse> find(@PathVariable UUID id) {
        return ApiResponse.ok(directory.find(id));
    }

    @PostMapping("/batch")
    public ApiResponse<Map<UUID, UserBasicResponse>> findAll(@RequestBody @Size(max = 100) Collection<UUID> ids) {
        return ApiResponse.ok(directory.findAll(ids));
    }
}
