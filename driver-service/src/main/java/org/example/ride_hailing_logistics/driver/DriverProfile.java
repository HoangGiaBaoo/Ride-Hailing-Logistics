package org.example.ride_hailing_logistics.driver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.driver.DriverEnums.DriverAvailability;
import org.example.ride_hailing_logistics.driver.DriverEnums.ReviewStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Ho so tai xe. Trang thai kha dung (availability) KHONG doi qua setter cua entity nay
 * ma qua cap nhat co dieu kien trong DriverProfileRepository, de nguyen tu duoi dong thoi (FR-DRV-012).
 */
@Entity
@Table(name = "driver_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverProfile {

    private static final EnumSet<ReviewStatus> EDITABLE = EnumSet.of(ReviewStatus.DRAFT, ReviewStatus.REJECTED);

    @Id
    private UUID id;

    @Column(name = "license_number", length = 30)
    private String licenseNumber;

    @Column(name = "license_class", length = 10)
    private String licenseClass;

    @Column(name = "license_expiry")
    private LocalDate licenseExpiry;

    @Column(name = "national_id_number", length = 20)
    private String nationalIdNumber;

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    @Column(name = "review_reason", length = 500)
    private String reviewReason;

    @Column(name = "profile_version", nullable = false)
    private int profileVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriverAvailability availability;

    @Column(name = "availability_changed_at", nullable = false)
    private Instant availabilityChangedAt;

    // Offer (OFFERED) hoac chuyen (BUSY) dang giu tai xe; chi doi qua cap nhat co dieu kien trong repository
    @Column(name = "availability_ref")
    private UUID availabilityRef;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    // Wrapper (khong phai long): version null = entity moi -> Spring Data goi persist() chu khong merge()
    private Long version;

    static DriverProfile draft(UUID userId, Instant now) {
        DriverProfile p = new DriverProfile();
        p.id = userId;
        p.reviewStatus = ReviewStatus.DRAFT;
        p.profileVersion = 1;
        p.availability = DriverAvailability.OFFLINE;
        p.availabilityChangedAt = now;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public boolean isEditable() {
        return EDITABLE.contains(reviewStatus);
    }

    void assertEditable() {
        if (!isEditable()) {
            throw BusinessException.rule("Hồ sơ đang ở trạng thái " + reviewStatus
                    + ", chỉ sửa được khi ở trạng thái nháp hoặc bị từ chối.");
        }
    }

    void updatePersonal(String licenseNumber, String licenseClass, LocalDate licenseExpiry, String nationalIdNumber,
                        String address, Instant now) {
        assertEditable();
        this.licenseNumber = licenseNumber;
        this.licenseClass = licenseClass;
        this.licenseExpiry = licenseExpiry;
        this.nationalIdNumber = nationalIdNumber;
        this.address = address;
        touch(now);
    }

    /** Moi thay doi noi dung tang phien ban, de quyet dinh duyet gan dung phien ban da xem (FR-DRV-006). */
    void touch(Instant now) {
        if (reviewStatus == ReviewStatus.DRAFT || reviewStatus == ReviewStatus.REJECTED) {
            profileVersion++;
        }
        updatedAt = now;
    }

    void submit(Instant now) {
        assertEditable();
        reviewStatus = ReviewStatus.PENDING_REVIEW;
        reviewReason = null;
        submittedAt = now;
        updatedAt = now;
    }

    void approve(Instant now) {
        requirePending();
        reviewStatus = ReviewStatus.APPROVED;
        reviewReason = null;
        approvedAt = now;
        updatedAt = now;
    }

    void reject(String reason, Instant now) {
        requirePending();
        reviewStatus = ReviewStatus.REJECTED;
        reviewReason = reason;
        updatedAt = now;
    }

    void requestChanges(String reason, Instant now) {
        requirePending();
        reviewStatus = ReviewStatus.DRAFT;
        reviewReason = reason;
        updatedAt = now;
    }

    void suspend(String reason, Instant now) {
        if (reviewStatus != ReviewStatus.APPROVED) {
            throw BusinessException.rule("Chỉ đình chỉ được tài xế đã được duyệt.");
        }
        reviewStatus = ReviewStatus.SUSPENDED;
        reviewReason = reason;
        updatedAt = now;
    }

    private void requirePending() {
        if (reviewStatus != ReviewStatus.PENDING_REVIEW) {
            throw BusinessException.rule("Hồ sơ không ở trạng thái chờ duyệt.");
        }
    }
}
