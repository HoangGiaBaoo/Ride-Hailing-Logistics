package org.example.ride_hailing_logistics.driver;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.driver.DriverDtos.DriverDetailResponse;
import org.example.ride_hailing_logistics.driver.DriverDtos.DriverSummaryResponse;
import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentStatus;
import org.example.ride_hailing_logistics.driver.DriverEnums.ReviewDecisionType;
import org.example.ride_hailing_logistics.driver.DriverEnums.ReviewStatus;
import org.example.ride_hailing_logistics.driver.DriverEnums.VehicleStatus;
import org.example.ride_hailing_logistics.identity.UserDirectory;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.example.ride_hailing_logistics.wallet.WalletService;
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

/** Nhan vien kiem duyet xu ly ho so tai xe (FR-DRV-005, FR-DRV-006, UC-01). */
@Service
@RequiredArgsConstructor
public class DriverReviewService {

    private final DriverProfileRepository profiles;
    private final VehicleRepository vehicles;
    private final DriverDocumentRepository documents;
    private final DriverReviewDecisionRepository decisions;
    private final DriverProfileService profileService;
    private final DriverAvailabilityService availabilityService;
    private final DocumentStorage storage;
    private final UserDirectory userDirectory;
    private final WalletService walletService;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<DriverSummaryResponse> list(ReviewStatus status, Pageable pageable) {
        var page = profiles.findByReviewStatus(status, pageable);
        Map<UUID, UserDirectory.UserBasic> users = userDirectory.findAll(page.map(DriverProfile::getId).getContent());
        return PageResponse.of(page, p -> new DriverSummaryResponse(p.getId(),
                users.containsKey(p.getId()) ? users.get(p.getId()).fullName() : null,
                p.getReviewStatus(), p.getAvailability(), p.getSubmittedAt()));
    }

    @Transactional(readOnly = true)
    public DriverDetailResponse detail(UUID driverId) {
        return profileService.detail(driverId);
    }

    /** Xem tep tai lieu la truy cap du lieu nhay cam -> ghi audit (DR-PRV-003, NFR-PRV-004). */
    @Transactional
    public DocumentFile documentFile(UUID documentId, Actor actor) {
        DriverDocument doc = documents.findById(documentId).orElseThrow(() -> BusinessException.notFound("tài liệu"));
        audit.record(actor, "DRIVER_DOCUMENT_VIEWED", "DriverDocument", documentId,
                Map.of("driverId", doc.getDriverId(), "type", doc.getDocType()));
        return new DocumentFile(storage.read(doc.getStorageName()), doc.getContentType());
    }

    @Transactional
    public DriverDetailResponse decide(UUID driverId, ReviewDecisionType decision, String reason, Actor actor) {
        DriverProfile profile = profiles.findById(driverId).orElseThrow(() -> BusinessException.notFound("hồ sơ tài xế"));
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
                walletService.openWallet(driverId);   // FR-WAL-001: moi tai xe duoc duyet co mot vi
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

    @Transactional
    public DriverDetailResponse suspend(UUID driverId, String reason, Actor actor) {
        DriverProfile profile = profiles.findById(driverId).orElseThrow(() -> BusinessException.notFound("hồ sơ tài xế"));
        Instant now = clock.instant();
        profile.suspend(reason, now);
        profiles.flush();
        // Dang ranh thi Offline ngay; dang offer/chuyen thi khi ket thuc se ve OFFLINE (releaseFrom kiem review_status)
        availabilityService.forceOfflineIfIdle(driverId);
        decisions.save(DriverReviewDecision.of(driverId, actor.id(), ReviewDecisionType.SUSPEND, reason,
                profile.getProfileVersion(), now));
        audit.record(actor, "DRIVER_SUSPENDED", "DriverProfile", driverId, Map.of("reason", reason));
        return profileService.detail(driverId);
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng nhập lý do.");
        }
        return reason.trim();
    }

    public record DocumentFile(byte[] content, String contentType) {}
}
