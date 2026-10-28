package org.example.ride_hailing_logistics.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewaySecurityConfig {

    /** Cung khoa, cung thuat toan voi identity-service; kiem chu ky + han (lech dong ho 30 s) + issuer. */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(GatewayProperties props) {
        byte[] secret = props.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            // HS256 can khoa >= 256 bit; khoa ngan thi dung ngay tu luc khoi dong thay vi chap nhan token yeu
            throw new IllegalStateException("app.gateway.jwt-secret must be at least 32 bytes");
        }
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                .withSecretKey(new SecretKeySpec(secret, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)), new JwtIssuerValidator(props.getJwtIssuer())));
        return decoder;
    }
}
