package org.example.ride_hailing_logistics.driver.service;

import org.example.ride_hailing_logistics.driver.dto.request.UpdateDriverProfileRequest;
import org.example.ride_hailing_logistics.driver.dto.request.VehicleRequest;
import org.example.ride_hailing_logistics.driver.dto.response.DocumentResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverDetailResponse;
import org.example.ride_hailing_logistics.driver.dto.response.OnlineEligibilityResponse;
import org.example.ride_hailing_logistics.driver.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Tai xe tu quan ly ho so, phuong tien, tai lieu va gui duyet (UC-01). */
public interface DriverProfileService {

    void createDraftProfile(UUID userId, String fullName, String phone);

    void syncAccount(UUID userId, String fullName, Boolean active, Instant changedAt);

    DriverDetailResponse detail(UUID driverId);

    DriverDetailResponse updateProfile(UUID driverId, UpdateDriverProfileRequest request);

    DriverDetailResponse upsertVehicle(UUID driverId, VehicleRequest request);

    DocumentResponse uploadDocument(UUID driverId, DocumentType type, String number, LocalDate expiresAt, MultipartFile file);

    DriverDetailResponse submit(UUID driverId);

    /** Dieu kien Online (FR-DRV-008, BR-001) + ban chup tai xe/xe cho trip-service. */
    OnlineEligibilityResponse onlineEligibility(UUID driverId);
}
