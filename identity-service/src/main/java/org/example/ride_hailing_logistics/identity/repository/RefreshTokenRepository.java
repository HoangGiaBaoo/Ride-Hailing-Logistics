package org.example.ride_hailing_logistics.identity.repository;

import org.example.ride_hailing_logistics.identity.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Xoay vong nguyen tu: hai request cung dung mot refresh token thi chi mot ben doi duoc (so dong = 1). */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now, t.replacedBy = :replacement where t.id = :id and t.revokedAt is null")
    int rotate(@Param("id") UUID id, @Param("replacement") UUID replacement, @Param("now") Instant now);
}
