package org.example.ride_hailing_logistics.platform.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    // Hai request cung khoa den cung luc: unique constraint bat request thu hai cho, roi nhan 0 dong
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records (id, user_id, scope, idem_key, request_hash, status, created_at)
            VALUES (:id, :userId, :scope, :key, :hash, 'IN_PROGRESS', :now)
            ON CONFLICT (user_id, scope, idem_key) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("userId") UUID userId, @Param("scope") String scope,
              @Param("key") String key, @Param("hash") String hash, @Param("now") Instant now);

    Optional<IdempotencyRecord> findByUserIdAndScopeAndIdemKey(UUID userId, String scope, String idemKey);

    @Modifying
    @Query(value = """
            UPDATE idempotency_records SET status = 'COMPLETED', response_body = :body, completed_at = :now
            WHERE user_id = :userId AND scope = :scope AND idem_key = :key
            """, nativeQuery = true)
    int complete(@Param("userId") UUID userId, @Param("scope") String scope, @Param("key") String key,
                 @Param("body") String body, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            DELETE FROM idempotency_records
            WHERE user_id = :userId AND scope = :scope AND idem_key = :key AND status = 'IN_PROGRESS'
            """, nativeQuery = true)
    int release(@Param("userId") UUID userId, @Param("scope") String scope, @Param("key") String key);

    // Ban ghi IN_PROGRESS bi bo lai do tien trinh chet giua chung: cho phep chay lai sau mot luc
    @Modifying
    @Query(value = """
            UPDATE idempotency_records SET created_at = :now
            WHERE user_id = :userId AND scope = :scope AND idem_key = :key
              AND status = 'IN_PROGRESS' AND created_at < :staleBefore
            """, nativeQuery = true)
    int reclaimStale(@Param("userId") UUID userId, @Param("scope") String scope, @Param("key") String key,
                     @Param("staleBefore") Instant staleBefore, @Param("now") Instant now);
}
