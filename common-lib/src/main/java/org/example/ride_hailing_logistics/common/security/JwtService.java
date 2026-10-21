package org.example.ride_hailing_logistics.common.security;

import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    public static final String SERVICE_ROLE = "SERVICE";

    private final JwtEncoder encoder;
    private final AppProperties.Jwt props;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, AppProperties appProperties, Clock clock) {
        this.encoder = encoder;
        this.props = appProperties.security().jwt();
        this.clock = clock;
    }

    public IssuedToken issueAccessToken(UUID userId, Collection<Role> roles) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(props.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", roles.stream().map(Enum::name).sorted().toList())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * Token cho loi goi noi bo giua cac service (/internal/**): ky cung khoa, vai tro SERVICE,
     * khong mang danh tinh nguoi dung nao. Gateway khong dinh tuyen /internal nen client ben ngoai khong goi duoc.
     */
    public IssuedToken issueServiceToken(String serviceName, Duration ttl) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject("service:" + serviceName)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("roles", List.of(SERVICE_ROLE))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {}
}
