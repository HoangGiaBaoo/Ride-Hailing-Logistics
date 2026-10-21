package org.example.ride_hailing_logistics.driver.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "identity-service", contextId = "driverIdentityClient")
public interface IdentityClient {

    @GetMapping("/internal/users/{id}")
    ApiResponse<UserDirectory.UserBasic> find(@PathVariable("id") UUID id);

    @PostMapping("/internal/users/batch")
    ApiResponse<Map<UUID, UserDirectory.UserBasic>> findAll(@RequestBody Collection<UUID> ids);
}
