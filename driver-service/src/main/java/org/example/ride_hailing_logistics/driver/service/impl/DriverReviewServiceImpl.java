package org.example.ride_hailing_logistics.driver.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.driver.domain.DriverEligibility;
import org.example.ride_hailing_logistics.driver.dto.response.DocumentFileResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverDetailResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverSummaryResponse;
import org.example.ride_hailing_logistics.driver.entity.DriverDocument;
import org.example.ride_hailing_logistics.driver.entity.DriverProfile;
import org.example.ride_hailing_logistics.driver.entity.DriverReviewDecision;
import org.example.ride_hailing_logistics.driver.entity.Vehicle;
import org.example.ride_hailing_logistics.driver.enums.DocumentStatus;
import org.example.ride_hailing_logistics.driver.enums.ReviewDecisionType;
import org.example.ride_hailing_logistics.driver.enums.ReviewStatus;
import org.example.ride_hailing_logistics.driver.enums.VehicleStatus;
import org.example.ride_hailing_logistics.driver.event.DriverApproved;
import org.example.ride_hailing_logistics.driver.event.DriverEvents;
import org.example.ride_hailing_logistics.driver.event.DriverSuspended;
import org.example.ride_hailing_logistics.driver.exception.DriverProfileNotFoundException;
import org.example.ride_hailing_logistics.driver.mapper.DriverMapper;
import org.example.ride_hailing_logistics.driver.repository.DriverDocumentRepository;
import org.example.ride_hailing_logistics.driver.repository.DriverProfileRepository;
import org.example.ride_hailing_logistics.driver.repository.DriverReviewDecisionRepository;
import org.example.ride_hailing_logistics.driver.repository.VehicleRepository;
import org.example.ride_hailing_logistics.driver.service.DocumentStorageService;
import org.example.ride_hailing_logistics.driver.service.DriverProfileService;
import org.example.ride_hailing_logistics.driver.service.DriverReviewService;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverReviewServiceImpl implements DriverReviewService {

    private final DriverProfileRepository profiles;
    private final VehicleRepository vehicles;
    private final DriverDocumentRepository documents;
    private final DriverReviewDecisionRepository decisions;
    private final DriverProfileService profileService;
    private final DocumentStorageService storage;
    private final DriverMapper mapper;
    private final OutboxService outbox;
    private final AuditService audit;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DriverSummaryResponse> list(ReviewStatus status, Pageable pageable) {
        return PageResponse.of(profiles.findByReviewStatus(status, pageable), mapper::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public DriverDetailResponse detail(UUID driverId) {
        return profileService.detail(driverId);
    }

    /** Xem tep tai lieu la truy cap du lieu nhay cam -> ghi audit (DR-PRV-003, NFR-PRV-004). */
    @Override
    @Transactional
    public DocumentFileResponse documentFile(UUID documentId, Actor actor) {
        DriverDocument doc = documents.findById(documentId).orElseThrow(() -> BusinessException.notFound("tài liệu"));
        audit.record(actor, "DRIVER_DOCUMENT_VIEWED", "DriverDocument", documentId,
                Map.of("driverId", doc.getDriverId(), "type", doc.getDocType()));
        return new DocumentFileResponse(storage.read(doc.getStorageName()), doc.getContentType());
    }

    @Override
    @Transactional
    public DriverDetailResponse decide(UUID driverId, ReviewDecisionType decision, String reason, Actor actor) {
        DriverProfile profile = load(driverId);
        Instant now = clock.instant();
        List<DriverDocument> docs = documents.findByDriverIdAndStatusNot(driverId, DocumentStatus.SUPERSEDED);
        switch (decision) {
            case APPROVE -> {
                Vehicle vehicle = vehicles.findByDriverIdAndStatus(driverId, VehicleStatus.ACTIVE).orElse(null);
                List<String> missing = DriverEligibility.missingForSubmission(profile, vehicle, docs,
                        LocalDate.ofInstant(now, ZoneOffset.UTC));
                if (!missing.isEmpty()) {
                    // Ho so thieu/het han khong duoc duyet (tieu chi nghiem thu UC-01)
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Hồ sơ chưa đủ điều kiện duyệt.",
                            Map.of("missing", missing));
                }
                profile.approve(now);
                docs.forEach(DriverDocument::approve);
                // FR-WAL-001: payment-service nghe event nay va mo vi cho tai xe
                outbox.append(DriverEvents.PRODUCER, "Driver", driverId, DriverEvents.DRIVER_APPROVED, 1,
                        new DriverApproved(driverId, now));
            }
            case REJECT -> {
                profile.reject(requireReason(reason), now);
                docs.forEach(DriverDocument::reject);
            }
            case REQUEST_CHANGES -> profile.requestChanges(requireReason(reason), now);
            case SUSPEND -> throw BusinessException.rule("Dùng chức năng đình chỉ của quản trị viên.");
        }
        decisions.save(DriverReviewDecision.of(driverId, actor.id(), decision, reason, profile.getProfileVersion(), now));
        audit.record(actor, "DRIVER_REVIEW_" + decision, "DriverProfile", driverId,
                Map.of("profileVersion", profile.getProfileVersion(), "reason", reason == null ? "" : reason));
        return profileService.detail(driverId);
    }

    @Override
    @Transactional
    public DriverDetailResponse suspend(UUID driverId, String reason, Actor actor) {
        DriverProfile profile = load(driverId);
        Instant now = clock.instant();
        profile.suspend(reason, now);
        // trip-service giu trang thai kha dung: dang ranh -> Offline ngay, dang offer/chuyen -> Offline khi ket thuc
        outbox.append(DriverEvents.PRODUCER, "Driver", driverId, DriverEvents.DRIVER_SUSPENDED, 1,
                new DriverSuspended(driverId, reason, now));
        decisions.save(DriverReviewDecision.of(driverId, actor.id(), ReviewDecisionType.SUSPEND, reason,
                profile.getProfileVersion(), now));
        audit.record(actor, "DRIVER_SUSPENDED", "DriverProfile", driverId, Map.of("reason", reason));
        return profileService.detail(driverId);
    }

    private DriverProfile load(UUID driverId) {
        return profiles.findById(driverId).orElseThrow(DriverProfileNotFoundException::new);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng nhập lý do.");
        }
        return reason.trim();
    }
}
