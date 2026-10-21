package org.example.ride_hailing_logistics.platform.idempotency;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.util.Secrets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Idempotency-Key cho cac thao tac co hieu ung nghiep vu (COM-008, BR-015).
 *
 * Lan dau: giu cho khoa (IN_PROGRESS) trong transaction rieng, chay thao tac, luu ket qua.
 * Lan sau cung khoa + cung payload: tra lai dung ket qua cu. Khac payload: IDEMPOTENCY_KEY_REUSED.
 * Thao tac that bai: nha khoa ra de client thu lai voi cung khoa.
 */
@Slf4j
@Service
public class IdempotencyService {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.:-]{8,100}");
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);

    private final IdempotencyRepository repository;
    private final ObjectMapper mapper;
    private final TransactionTemplate requiresNew;
    private final Clock clock;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper mapper,
                              PlatformTransactionManager txManager, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public <T> T execute(UUID userId, String scope, String key, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key phải dài 8–100 ký tự, chỉ gồm chữ, số và . _ : -");
        }
        String hash = Secrets.sha256Hex(scope + "|" + mapper.writeValueAsString(request));

        Integer claimed = requiresNew.execute(s -> repository.claim(UUID.randomUUID(), userId, scope, key, hash, clock.instant()));
        if (claimed == null || claimed == 0) {
            T replay = replayOrReject(userId, scope, key, hash, responseType);
            if (replay != null) {
                return replay;
            }
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException ex) {
            requiresNew.executeWithoutResult(s -> repository.release(userId, scope, key));
            throw ex;
        }
        String body = mapper.writeValueAsString(result);
        requiresNew.executeWithoutResult(s -> repository.complete(userId, scope, key, body, clock.instant()));
        return result;
    }

    /** Tra ket qua cu, hoac null neu ban ghi IN_PROGRESS da cu (cho phep chay lai). */
    private <T> T replayOrReject(UUID userId, String scope, String key, String hash, Class<T> responseType) {
        IdempotencyRecord existing = repository.findByUserIdAndScopeAndIdemKey(userId, scope, key)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT, "Yêu cầu đang được xử lý, vui lòng thử lại."));
        if (!existing.getRequestHash().equals(hash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Idempotency-Key này đã được dùng cho một yêu cầu khác.");
        }
        if (existing.isCompleted()) {
            return mapper.readValue(existing.getResponseBody(), responseType);
        }
        Instant now = clock.instant();
        Integer reclaimed = requiresNew.execute(s -> repository.reclaimStale(userId, scope, key, now.minus(STALE_AFTER), now));
        if (reclaimed != null && reclaimed == 1) {
            // Lan truoc chet giua chung; rang buoc nghiep vu (quote dung mot lan, unique index) van chan hieu ung trung
            log.warn("Reclaiming stale idempotency record scope={} user={}", scope, userId);
            return null;
        }
        throw new BusinessException(ErrorCode.CONFLICT, "Yêu cầu đang được xử lý, vui lòng thử lại sau giây lát.");
    }
}
