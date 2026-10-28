package org.example.ride_hailing_logistics.driver.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.util.Normalizers;
import org.example.ride_hailing_logistics.driver.domain.DriverEligibility;
import org.example.ride_hailing_logistics.driver.dto.request.UpdateDriverProfileRequest;
import org.example.ride_hailing_logistics.driver.dto.request.VehicleRequest;
import org.example.ride_hailing_logistics.driver.dto.response.DocumentResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverDetailResponse;
import org.example.ride_hailing_logistics.driver.dto.response.OnlineEligibilityResponse;
import org.example.ride_hailing_logistics.driver.entity.DriverDocument;
import org.example.ride_hailing_logistics.driver.entity.DriverProfile;
import org.example.ride_hailing_logistics.driver.entity.Vehicle;
import org.example.ride_hailing_logistics.driver.enums.DocumentStatus;
import org.example.ride_hailing_logistics.driver.enums.DocumentType;
import org.example.ride_hailing_logistics.driver.enums.VehicleStatus;
import org.example.ride_hailing_logistics.driver.exception.DriverProfileNotFoundException;
import org.example.ride_hailing_logistics.driver.mapper.DriverMapper;
import org.example.ride_hailing_logistics.driver.repository.DriverDocumentRepository;
import org.example.ride_hailing_logistics.driver.repository.DriverProfileRepository;
import org.example.ride_hailing_logistics.driver.repository.DriverReviewDecisionRepository;
import org.example.ride_hailing_logistics.driver.repository.VehicleRepository;
import org.example.ride_hailing_logistics.driver.service.DocumentStorageService;
import org.example.ride_hailing_logistics.driver.service.DriverProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverProfileServiceImpl implements DriverProfileService {

    private final DriverProfileRepository profiles;
    private final VehicleRepository vehicles;
    private final DriverDocumentRepository documents;
    private final DriverReviewDecisionRepository decisions;
    private final DocumentStorageService storage;
    private final DriverMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public void createDraftProfile(UUID userId, String fullName, String phone) {
        if (!profiles.existsById(userId)) {
            profiles.save(DriverProfile.draft(userId, fullName, phone, clock.instant()));
        }
    }

    @Override
    @Transactional
    public void syncAccount(UUID userId, String fullName, Boolean active, Instant changedAt) {
        // Chi tai khoan tai xe co ho so; ban tin cu hon lan cap nhat cuoi thi bo qua (Kafka giao lai / lech thu tu)
        profiles.findById(userId)
                .filter(p -> changedAt == null || !changedAt.isBefore(p.getUpdatedAt()))
                .ifPresent(p -> p.syncAccount(fullName, active, changedAt == null ? clock.instant() : changedAt));
    }

    @Override
    @Transactional(readOnly = true)
    public DriverDetailResponse detail(UUID driverId) {
        DriverProfile profile = load(driverId);
        Vehicle vehicle = activeVehicle(driverId);
        List<DriverDocument> docs = documents.findByDriverIdAndStatusNot(driverId, DocumentStatus.SUPERSEDED);
        List<String> missing = profile.isEditable()
                ? DriverEligibility.missingForSubmission(profile, vehicle, docs, today()) : List.of();
        return new DriverDetailResponse(driverId, profile.getFullName(), profile.getLicenseNumber(),
                profile.getLicenseClass(), profile.getLicenseExpiry(), profile.getNationalIdNumber(), profile.getAddress(),
                profile.getReviewStatus(), profile.getReviewReason(), profile.getProfileVersion(),
                vehicle == null ? null : mapper.toResponse(vehicle), mapper.toDocumentResponses(docs),
                mapper.toDecisionResponses(decisions.findByDriverIdOrderByCreatedAtAsc(driverId)), missing);
    }

    @Override
    @Transactional
    public DriverDetailResponse updateProfile(UUID driverId, UpdateDriverProfileRequest req) {
        if (!req.licenseExpiry().isAfter(today())) {
            throw BusinessException.rule("Giấy phép lái xe đã hết hạn.");
        }
        load(driverId).updatePersonal(req.licenseNumber().trim(), req.licenseClass().trim().toUpperCase(),
                req.licenseExpiry(), req.nationalIdNumber().trim(), req.address().trim(), clock.instant());
        return detail(driverId);
    }

    @Override
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

    @Override
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
        DocumentStorageService.StoredFile stored = storage.store(file);
        // Ban cu cung loai bi thay the; chi mot ban hien hanh cho moi loai (unique index)
        documents.findByDriverIdAndStatusNot(driverId, DocumentStatus.SUPERSEDED).stream()
                .filter(d -> d.getDocType() == type)
                .forEach(DriverDocument::supersede);
        documents.flush();
        Instant now = clock.instant();
        DriverDocument doc = documents.save(DriverDocument.create(driverId, type, number.trim(), expiresAt, stored, now));
        profile.touch(now);
        return mapper.toResponse(doc);
    }

    @Override
    @Transactional
    public DriverDetailResponse submit(UUID driverId) {
        DriverProfile profile = load(driverId);
        Vehicle vehicle = activeVehicle(driverId);
        List<DriverDocument> docs = documents.findByDriverIdAndStatusNot(driverId, DocumentStatus.SUPERSEDED);
        List<String> missing = DriverEligibility.missingForSubmission(profile, vehicle, docs, today());
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Hồ sơ chưa đầy đủ.", Map.of("missing", missing));
        }
        profile.submit(clock.instant());
        return detail(driverId);
    }

    @Override
    @Transactional(readOnly = true)
    public OnlineEligibilityResponse onlineEligibility(UUID driverId) {
        DriverProfile profile = load(driverId);
        Vehicle vehicle = activeVehicle(driverId);
        List<String> reasons = DriverEligibility.blockingOnline(profile.isAccountActive(), profile, vehicle,
                documents.findByDriverIdAndStatusNot(driverId, DocumentStatus.SUPERSEDED), today());
        return new OnlineEligibilityResponse(reasons.isEmpty(), reasons,
                vehicle == null ? null : mapper.toSnapshot(profile, vehicle));
    }

    private Vehicle activeVehicle(UUID driverId) {
        return vehicles.findByDriverIdAndStatus(driverId, VehicleStatus.ACTIVE).orElse(null);
    }

    private DriverProfile load(UUID driverId) {
        return profiles.findById(driverId).orElseThrow(DriverProfileNotFoundException::new);
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
