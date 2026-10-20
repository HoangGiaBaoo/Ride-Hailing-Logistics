package org.example.ride_hailing_logistics.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Ban ghi audit bat bien (FR-ADM-005, FR-ADM-006); DB co trigger chan UPDATE/DELETE. */
@Entity
@Immutable
@Table(name = "audit_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditRecord {

    @Id
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_roles", length = 200)
    private String actorRoles;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(columnDefinition = "text")
    private String delta;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    static AuditRecord of(UUID actorId, String actorRoles, String action, String targetType, String targetId,
                          String result, String delta, String correlationId, Instant now) {
        AuditRecord r = new AuditRecord();
        r.id = UUID.randomUUID();
        r.actorId = actorId;
        r.actorRoles = actorRoles;
        r.action = action;
        r.targetType = targetType;
        r.targetId = targetId;
        r.result = result;
        r.delta = delta;
        r.correlationId = correlationId;
        r.createdAt = now;
        return r;
    }
}
