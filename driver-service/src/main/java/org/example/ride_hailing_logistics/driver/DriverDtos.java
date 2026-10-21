package org.example.ride_hailing_logistics.driver;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentStatus;
import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentType;
import org.example.ride_hailing_logistics.driver.DriverEnums.DriverAvailability;
import org.example.ride_hailing_logistics.driver.DriverEnums.ReviewDecisionType;
import org.example.ride_hailing_logistics.driver.DriverEnums.ReviewStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DriverDtos {

    private DriverDtos() {
    }

    public record UpdateDriverProfileRequest(
            @NotBlank @Size(max = 30) String licenseNumber,
            @NotBlank @Size(max = 10) String licenseClass,
            @NotNull LocalDate licenseExpiry,
            @NotBlank @Size(min = 9, max = 20) String nationalIdNumber,
            @NotBlank @Size(max = 255) String address) {}

    public record VehicleRequest(
            @NotNull VehicleType vehicleType,
            @NotBlank @Size(max = 15) String plateNumber,
            @NotBlank @Size(max = 50) String brand,
            @NotBlank @Size(max = 50) String model,
            @NotBlank @Size(max = 30) String color,
            @Min(1990) @Max(2100) int manufactureYear) {}

    public record ReviewDecisionRequest(@NotNull ReviewDecisionType decision, @Size(max = 500) String reason) {}

    public record SuspendRequest(@NotBlank @Size(max = 500) String reason) {}

    public record VehicleResponse(UUID id, VehicleType vehicleType, String plateNumber, String brand, String model,
                                  String color, int manufactureYear) {
        static VehicleResponse from(Vehicle v) {
            return v == null ? null : new VehicleResponse(v.getId(), v.getVehicleType(), v.getPlateNumber(), v.getBrand(),
                    v.getModel(), v.getColor(), v.getManufactureYear());
        }
    }

    public record DocumentResponse(UUID id, DocumentType type, String maskedNumber, LocalDate expiresAt,
                                   DocumentStatus status, String contentType, long fileSize, Instant uploadedAt) {
        static DocumentResponse from(DriverDocument d) {
            return new DocumentResponse(d.getId(), d.getDocType(), d.maskedNumber(), d.getExpiresAt(), d.getStatus(),
                    d.getContentType(), d.getFileSize(), d.getCreatedAt());
        }
    }

    public record DecisionResponse(ReviewDecisionType decision, String reason, UUID reviewerId, int profileVersion,
                                   Instant createdAt) {
        static DecisionResponse from(DriverReviewDecision d) {
            return new DecisionResponse(d.getDecision(), d.getReason(), d.getReviewerId(), d.getProfileVersion(), d.getCreatedAt());
        }
    }

    public record DriverDetailResponse(UUID driverId, String fullName, String licenseNumber, String licenseClass,
                                       LocalDate licenseExpiry, String nationalIdNumber, String address,
                                       ReviewStatus reviewStatus, String reviewReason, int profileVersion,
                                       DriverAvailability availability, VehicleResponse vehicle,
                                       List<DocumentResponse> documents, List<DecisionResponse> decisions,
                                       List<String> missingRequirements) {}

    public record DriverSummaryResponse(UUID driverId, String fullName, ReviewStatus reviewStatus,
                                        DriverAvailability availability, Instant submittedAt) {}

    public record AvailabilityResponse(DriverAvailability availability, List<String> blockingReasons) {}

    /** Thong tin tai xe duoc chup vao chuyen khi ghep (UI-CUS-005). */
    public record DriverSnapshot(UUID driverId, String fullName, String phone, UUID vehicleId, VehicleType vehicleType,
                                 String plateNumber, String vehicleDescription) {}
}
