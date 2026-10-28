package org.example.ride_hailing_logistics.gateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Kiem token da bi thu hoi truoc han, doc CUNG khoa Redis voi TokenRevocationStore (common-lib):
 * - auth:blacklist:{jti}         — dang xuat (TTL = thoi gian song con lai cua token)
 * - auth:token-version:{userId}  — doi mat khau / khoa tai khoan / doi vai tro: token mang tv cu bi tu choi
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReactiveTokenRevocationChecker {

    static final String BLACKLIST_PREFIX = "auth:blacklist:";
    static final String TOKEN_VERSION_PREFIX = "auth:token-version:";
    static final String CLAIM_TOKEN_VERSION = "tv";

    private final ReactiveStringRedisTemplate redis;

    /**
     * true = token con hieu luc. Redis loi thi cho qua: moi service van kiem lai bang RevokedTokenValidator,
     * gateway khong nen la diem chet duy nhat khi Redis chap chon.
     */
    public Mono<Boolean> isActive(Jwt jwt) {
        Mono<Boolean> notBlacklisted = jwt.getId() == null ? Mono.just(true)
                : redis.hasKey(BLACKLIST_PREFIX + jwt.getId()).map(revoked -> !revoked);
        Mono<Boolean> versionCurrent = redis.opsForValue().get(TOKEN_VERSION_PREFIX + jwt.getSubject())
                .map(current -> tokenVersion(jwt) >= Long.parseLong(current))
                .defaultIfEmpty(true);
        return Mono.zip(notBlacklisted, versionCurrent, Boolean::logicalAnd)
                .onErrorResume(e -> {
                    log.warn("Token revocation check skipped: {}", e.toString());
                    return Mono.just(true);
                });
    }

    private static long tokenVersion(Jwt jwt) {
        Object tv = jwt.getClaim(CLAIM_TOKEN_VERSION);
        return tv instanceof Number n ? n.longValue() : 0L;
    }
}
