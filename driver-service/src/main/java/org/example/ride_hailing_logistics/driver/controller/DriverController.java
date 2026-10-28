package org.example.ride_hailing_logistics.driver.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.driver.dto.request.UpdateDriverProfileRequest;
import org.example.ride_hailing_logistics.driver.dto.request.VehicleRequest;
import org.example.ride_hailing_logistics.driver.dto.response.DocumentResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverDetailResponse;
import org.example.ride_hailing_logistics.driver.enums.DocumentType;
import org.example.ride_hailing_logistics.driver.service.DriverProfileService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;

@RestController
/** Trang thai Online/Offline va offer nam o trip-service; vi tri o location-service (gateway dinh tuyen theo duong dan). */
@RequestMapping("/api/v1/drivers/me")
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class DriverController {

    private final DriverProfileService profileService;

    @GetMapping
    public ApiResponse<DriverDetailResponse> me() {
        return ApiResponse.ok(profileService.detail(CurrentUser.id()));
    }

    @PutMapping("/profile")
    public ApiResponse<DriverDetailResponse> updateProfile(@Valid @RequestBody UpdateDriverProfileRequest request) {
        return ApiResponse.ok(profileService.updateProfile(CurrentUser.id(), request));
    }

    @PutMapping("/vehicle")
    public ApiResponse<DriverDetailResponse> upsertVehicle(@Valid @RequestBody VehicleRequest request) {
        return ApiResponse.ok(profileService.upsertVehicle(CurrentUser.id(), request));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DocumentResponse> upload(@RequestParam DocumentType type,
                                                @RequestParam String number,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresAt,
                                                @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(profileService.uploadDocument(CurrentUser.id(), type, number, expiresAt, file));
    }

    @PostMapping("/submit")
    public ApiResponse<DriverDetailResponse> submit() {
        return ApiResponse.ok(profileService.submit(CurrentUser.id()));
    }
}
