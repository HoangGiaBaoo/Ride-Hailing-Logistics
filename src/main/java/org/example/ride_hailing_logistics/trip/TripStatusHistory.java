package org.example.ride_hailing_logistics.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Lich su chuyen trang thai: cu/moi, actor, ly do, thoi diem, vi tri (FR-TRIP-010). Bat bien (DR-005). */
@Entity
@Immutable
@Table(name = "trip_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private TripStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private TripStatus toStatus;

    @Column(name = "actor_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 30)
    private TripActorRole actorRole;

    @Column(length = 500)
    private String reason;

    private Double lat;

    private Double lng;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static TripStatusHistory of(Trip trip, TripStatus from, TripStatus to, UUID actorId, TripActorRole role,
                                String reason, Double lat, Double lng, Instant now) {
        TripStatusHistory h = new TripStatusHistory();
        h.trip = trip;
        h.fromStatus = from;
        h.toStatus = to;
        h.actorId = actorId;
        h.actorRole = role;
        h.reason = reason;
        h.lat = lat;
        h.lng = lng;
        h.createdAt = now;
        return h;
    }
}
