package org.example.ride_hailing_logistics.identity;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.error.BusinessException;
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
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserDirectory directory;

    @GetMapping("/{id}")
    public ApiResponse<UserDirectory.UserBasic> find(@PathVariable UUID id) {
        return ApiResponse.ok(directory.find(id).orElseThrow(() -> BusinessException.notFound("tài khoản")));
    }

    @PostMapping("/batch")
    public ApiResponse<Map<UUID, UserDirectory.UserBasic>> findAll(@RequestBody Collection<UUID> ids) {
        return ApiResponse.ok(ids.isEmpty() ? Map.of() : directory.findAll(ids));
    }
}
