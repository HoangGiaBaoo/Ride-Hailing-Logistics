package org.example.ride_hailing_logistics.driver.dto.response;

import org.example.ride_hailing_logistics.driver.enums.ReviewStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DriverDetailResponse(UUID driverId, String fullName, String licenseNumber, String licenseClass,
                                   LocalDate licenseExpiry, String nationalIdNumber, String address,
                                   ReviewStatus reviewStatus, String reviewReason, int profileVersion,
                                   VehicleResponse vehicle, List<DocumentResponse> documents,
                                   List<DecisionResponse> decisions, List<String> missingRequirements) {}
