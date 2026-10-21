package org.example.ride_hailing_logistics.platform.audit;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.web.CorrelationId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    /** Ghi trong transaction nghiep vu: thao tac rollback thi audit cung khong con (khong ghi audit sai). */
    @Transactional
    public void record(Actor actor, String action, String targetType, Object targetId, Map<String, ?> delta) {
        record(actor, action, targetType, targetId, "SUCCESS", delta);
    }

    @Transactional
    public void record(Actor actor, String action, String targetType, Object targetId, String result, Map<String, ?> delta) {
        String deltaJson = delta == null || delta.isEmpty() ? null : mapper.writeValueAsString(delta);
        repository.save(AuditRecord.of(actor.id(), actor.rolesAsString(), action, targetType, String.valueOf(targetId),
                result, deltaJson, CorrelationId.current(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditView> search(String targetType, String targetId, UUID actorId, Pageable pageable) {
        // Specification thay vi "(:p is null or ...)": PostgreSQL khong suy duoc kieu tham so null
        Specification<AuditRecord> spec = (root, query, cb) -> cb.conjunction();
        if (targetType != null && !targetType.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("targetType"), targetType));
        if (targetId != null && !targetId.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("targetId"), targetId));
        if (actorId != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("actorId"), actorId));
        return PageResponse.of(repository.findAll(spec, pageable), AuditView::from);
    }

    public record AuditView(UUID id, UUID actorId, String actorRoles, String action, String targetType, String targetId,
                            String result, String delta, String correlationId, Instant createdAt) {
        static AuditView from(AuditRecord r) {
            return new AuditView(r.getId(), r.getActorId(), r.getActorRoles(), r.getAction(), r.getTargetType(),
                    r.getTargetId(), r.getResult(), r.getDelta(), r.getCorrelationId(), r.getCreatedAt());
        }
    }
}
