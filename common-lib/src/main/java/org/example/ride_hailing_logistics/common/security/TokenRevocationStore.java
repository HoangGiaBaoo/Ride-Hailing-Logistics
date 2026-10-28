package org.example.ride_hailing_logistics.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Thu hoi access token truoc han tren Redis (gateway doc cung cac khoa nay):
 * - dang xuat: dua jti vao danh sach den, TTL = thoi gian song con lai cua token -> Redis tu don
 * - doi mat khau / khoa tai khoan: tang token version, token mang version cu bi tu choi
 */
@Component
@RequiredArgsConstructor
public class TokenRevocationStore {

    public static final String BLACKLIST_PREFIX = "auth:blacklist:";
    public static final String TOKEN_VERSION_PREFIX = "auth:token-version:";

    private final StringRedisTemplate redis;

    public void revoke(String jti, Duration remaining) {
        if (jti == null || remaining.isNegative() || remaining.isZero()) return;
        redis.opsForValue().set(BLACKLIST_PREFIX + jti, "1", remaining);
    }

    public boolean isRevoked(String jti) {
        return jti != null && Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
    }

    /** Nguon su that la cot users.token_version; Redis chi la ban sao de gateway/service kiem nhanh. */
    public void publishVersion(UUID userId, long version) {
        redis.opsForValue().set(TOKEN_VERSION_PREFIX + userId, Long.toString(version));
    }

    public long currentVersion(UUID userId) {
        String v = redis.opsForValue().get(TOKEN_VERSION_PREFIX + userId);
        return v == null ? 0 : Long.parseLong(v);
    }
}
