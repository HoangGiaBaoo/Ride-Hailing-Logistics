package org.example.ride_hailing_logistics.driver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.example.ride_hailing_logistics.driver.DriverEnums.ReviewDecisionType;

import java.time.Instant;
import java.util.UUID;

/** Lich su quyet dinh xet duyet: ai, luc nao, ly do, phien ban ho so (FR-DRV-006). Bat bien. */
@Entity
@Immutable
@Table(name = "driver_review_decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverReviewDecision {

    @Id
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewDecisionType decision;

    @Column(length = 500)
    private String reason;

    @Column(name = "profile_version", nullable = false)
    private int profileVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static DriverReviewDecision of(UUID driverId, UUID reviewerId, ReviewDecisionType decision, String reason,
                                   int profileVersion, Instant now) {
        DriverReviewDecision d = new DriverReviewDecision();
        d.id = UUID.randomUUID();
        d.driverId = driverId;
        d.reviewerId = reviewerId;
        d.decision = decision;
        d.reason = reason;
        d.profileVersion = profileVersion;
        d.createdAt = now;
        return d;
    }
}
