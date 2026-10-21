package org.example.ride_hailing_logistics.driver;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.util.Normalizers;
import org.example.ride_hailing_logistics.driver.DriverDtos.DecisionResponse;
import org.example.ride_hailing_logistics.driver.DriverDtos.DocumentResponse;
import org.example.ride_hailing_logistics.driver.DriverDtos.DriverDetailResponse;
import org.example.ride_hailing_logistics.driver.DriverDtos.UpdateDriverProfileRequest;
import org.example.ride_hailing_logistics.driver.DriverDtos.VehicleRequest;
import org.example.ride_hailing_logistics.driver.DriverDtos.VehicleResponse;
import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentStatus;
import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentType;
import org.example.ride_hailing_logistics.driver.DriverEnums.VehicleStatus;
import org.example.ride_hailing_logistics.driver.client.UserDirectory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Tai xe tu quan ly ho so, phuong tien, tai lieu va gui duyet (UC-01). */
@Service
@RequiredArgsConstructor
public class DriverProfileService {

    private final DriverProfileRepository profiles;
    private final VehicleRepository vehicles;
    private final DriverDocumentRepository documents;
    private final DriverReviewDecisionRepository decisions;
    private final DocumentStorage storage;
    private final UserDirectory userDirectory;
    private final Clock clock;

    /** Goi tu identity khi tai khoan tai xe dang ky (cung transaction dang ky). */
    @Transactional
    public void createDraftProfile(UUID userId) {
        if (!profiles.existsById(userId)) {
            profiles.save(DriverProfile.draft(userId, clock.instant()));
        }
    }

    @Transactional(readOnly = true)
    public DriverDetailResponse detail(UUID driverId) {
        DriverProfile profile = load(driverId);
        Vehicle vehicle = vehicles.findByDriverIdAndStatus(driverId, VehicleStatus.ACTIVE).orElse(null);
        List<DriverDocument> docs = documents.findByDriverIdAndStatusNot(driverId, DocumentStatus.SUPERSEDED);
        String fullName = userDirectory.find(driverId).map(UserDirectory.UserBasic::fullName).orElse(null);
        List<String> missing = profile.isEditable() ? DriverEligibility.missingForSubmission(profile, vehicle, docs, today()) : List.of();
        return new DriverDetailResponse(driverId, fullName, profile.getLicenseNumber(), profile.getLicenseClass(),
                profile.getLicenseExpiry(), profile.getNationalIdNumber(), profile.getAddress(), profile.getReviewStatus(),
                profile.getReviewReason(), profile.getProfileVersion(), profile.getAvailability(),
                VehicleResponse.from(vehicle), docs.stream().map(DocumentResponse::from).toList(),
                decisions.findByDriverIdOrderByCreatedAtAsc(driverId).stream().map(DecisionResponse::from).toList(),
                missing);
    }

    @Transactional
    public DriverDetailResponse updateProfile(UUID driverId, UpdateDriverProfileRequest req) {
        if (!req.licenseExpiry().isAfter(today())) {
            throw BusinessException.rule("Giấy phép lái xe đã hết hạn.");
        }
        load(driverId).updatePersonal(req.licenseNumber().trim(), req.licenseClass().trim().toUpperCase(),
                req.licenseExpiry(), req.nationalIdNumber().trim(), req.address().trim(), clock.instant());
        return detail(driverId);
    }

    @Transactional
    public DriverDetailResponse upsertVehicle(UUID driverId, VehicleRequest req) {
        DriverProfile profile = load(driverId);
        profile.assertEditable();
        String plate = Normalizers.plate(req.plateNumber())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Biển số xe không hợp lệ."));
        vehicles.findByPlateNumber(plate)
                .filter(v -> !v.getDriverId().equals(driverId))
                .ifPresent(v -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "Biển số đã được đăng ký cho tài xế khác.");
                });
        Instant now = clock.instant();
        vehicles.findByDriverIdAndStatus(driverId, VehicleStatus.ACTIVE).ifPresentOrElse(
                v -> v.update(req.vehicleType(), plate, req.brand().trim(), req.model().trim(), req.color().trim(),
                        req.manufactureYear(), now),
                () -> vehicles.save(Vehicle.create(driverId, req.vehicleType(), plate, req.brand().trim(),
                        req.model().trim(), req.color().trim(), req.manufactureYear(), now)));
        profile.touch(now);
        return detail(driverId);
    }

    @Transactional
    public DocumentResponse uploadDocument(UUID driverId, DocumentType type, String number, LocalDate expiresAt,
                                           MultipartFile file) {
        DriverProfile profile = load(driverId);
        profile.assertEditable();
        if (number == null || number.isBlank() || number.length() > 50) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số giấy tờ không hợp lệ.");
        }
        if (expiresAt == null || !expiresAt.isAfter(today())) {
            throw BusinessException.rule("Tài liệu đã hết hạn hoặc thiếu ngày hết hạn.");
        }
        DocumentStorage.StoredFile stored = storage.store(file);
        // Ban cu cung loai bi thay the; chi mot ban hien hanh cho moi loai (unique index)
        documents.findByDriverIdAndStatusNot(driverId, DocumentStatus.SUPERSEDED).stream()
                .filter(d -> d.getDocType() == type)
                .forEach(DriverDocument::supersede);
        documents.flush();
        Instant now = clock.instant();
        DriverDocument doc = documents.save(DriverDocument.create(driverId, type, number.trim(), expiresAt, stored, now));
        profile.touch(now);
        return DocumentResponse.from(doc);
    }

    @Transactional
    public DriverDetailResponse submit(UUID driverId) {
        DriverProfile profile = load(driverId);
        Vehicle vehicle = vehicles.findByDriverIdAndStatus(driverId, VehicleStatus.ACTIVE).orElse(null);
        List<DriverDocument> docs = documents.findByDriverIdAndStatusNot(driverId, DocumentStatus.SUPERSEDED);
        List<String> missing = DriverEligibility.missingForSubmission(profile, vehicle, docs, today());
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Hồ sơ chưa đầy đủ.",
                    java.util.Map.of("missing", missing));
        }
        profile.submit(clock.instant());
        return detail(driverId);
    }

    private DriverProfile load(UUID driverId) {
        return profiles.findById(driverId).orElseThrow(() -> BusinessException.notFound("hồ sơ tài xế"));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
