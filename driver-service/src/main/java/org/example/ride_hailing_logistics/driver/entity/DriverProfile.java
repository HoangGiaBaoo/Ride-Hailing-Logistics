package org.example.ride_hailing_logistics.driver.entity;

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
import org.example.ride_hailing_logistics.driver.exception.ProfileNotEditableException;
import org.example.ride_hailing_logistics.driver.enums.ReviewStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Ho so tai xe (UC-01). Trang thai kha dung (AVAILABLE/OFFERED/BUSY) khong nam o day ma o trip-service,
 * de giu tai xe va gan chuyen cung mot transaction tren mot database (CON-06).
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

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(name = "account_active", nullable = false)
    private boolean accountActive = true;

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

    public static DriverProfile draft(UUID userId, String fullName, String phone, Instant now) {
        DriverProfile p = new DriverProfile();
        p.id = userId;
        p.reviewStatus = ReviewStatus.DRAFT;
        p.profileVersion = 1;
        p.fullName = fullName;
        p.phone = phone;
        p.accountActive = true;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    /** Ban chup tu identity-service; event den khong theo thu tu thi ban cu hon bi bo qua o handler. */
    public void syncAccount(String fullName, Boolean active, Instant now) {
        if (fullName != null) this.fullName = fullName;
        if (active != null) this.accountActive = active;
        this.updatedAt = now;
    }

    public boolean isEditable() {
        return EDITABLE.contains(reviewStatus);
    }

    public void assertEditable() {
        if (!isEditable()) {
            throw new ProfileNotEditableException(reviewStatus);
        }
    }

    public void updatePersonal(String licenseNumber, String licenseClass, LocalDate licenseExpiry, String nationalIdNumber,
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
    public void touch(Instant now) {
        if (reviewStatus == ReviewStatus.DRAFT || reviewStatus == ReviewStatus.REJECTED) {
            profileVersion++;
        }
        updatedAt = now;
    }

    public void submit(Instant now) {
        assertEditable();
        reviewStatus = ReviewStatus.PENDING_REVIEW;
        reviewReason = null;
        submittedAt = now;
        updatedAt = now;
    }

    public void approve(Instant now) {
        requirePending();
        reviewStatus = ReviewStatus.APPROVED;
        reviewReason = null;
        approvedAt = now;
        updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        requirePending();
        reviewStatus = ReviewStatus.REJECTED;
        reviewReason = reason;
        updatedAt = now;
    }

    public void requestChanges(String reason, Instant now) {
        requirePending();
        reviewStatus = ReviewStatus.DRAFT;
        reviewReason = reason;
        updatedAt = now;
    }

    public void suspend(String reason, Instant now) {
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
