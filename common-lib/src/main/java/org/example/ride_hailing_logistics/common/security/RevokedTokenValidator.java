package org.example.ride_hailing_logistics.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Tu choi access token da dang xuat (jti trong danh sach den) hoac mang token version cu. */
@RequiredArgsConstructor
public class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "Token revoked", null);

    private final TokenRevocationStore store;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        // Token SERVICE khong gan nguoi dung, song 5 phut, khong thu hoi
        if (jwt.getSubject() != null && jwt.getSubject().startsWith("service:")) {
            return OAuth2TokenValidatorResult.success();
        }
        if (store.isRevoked(jwt.getId())) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        Object tv = jwt.getClaims().get(JwtService.CLAIM_TOKEN_VERSION);
        long tokenVersion = tv instanceof Number n ? n.longValue() : 0;
        if (tokenVersion < store.currentVersion(UUID.fromString(jwt.getSubject()))) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
