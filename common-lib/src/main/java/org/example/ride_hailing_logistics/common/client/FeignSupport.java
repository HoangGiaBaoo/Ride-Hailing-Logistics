package org.example.ride_hailing_logistics.common.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.JwtService;
import org.example.ride_hailing_logistics.common.web.CorrelationId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cau hinh chung cho moi loi goi dong bo giua cac service (OpenFeign + Eureka, ten service thay cho dia chi).
 * - Gan token SERVICE va correlation ID vao moi request.
 * - Loi nghiep vu cua service dich (404, 409...) duoc dung lai thanh BusinessException cung ma,
 *   de service goi tra ve cho client dung loi do thay vi 500.
 */
@Slf4j
@Configuration
@EnableFeignClients(basePackages = "org.example.ride_hailing_logistics")
public class FeignSupport {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    @Bean
    public RequestInterceptor serviceAuthInterceptor(JwtService jwtService, Clock clock,
                                                     @Value("${spring.application.name}") String serviceName) {
        AtomicReference<JwtService.IssuedToken> cached = new AtomicReference<>();
        return template -> {
            JwtService.IssuedToken token = cached.get();
            // Lam moi truoc khi het han 1 phut de request dang bay khong mang token vua het han
            if (token == null || token.expiresAt().minus(Duration.ofMinutes(1)).isBefore(clock.instant())) {
                token = jwtService.issueServiceToken(serviceName, TOKEN_TTL);
                cached.set(token);
            }
            template.header("Authorization", "Bearer " + token.value());
            String correlationId = CorrelationId.current();
            if (correlationId != null) {
                template.header(CorrelationId.HEADER, correlationId);
            }
        };
    }

    @Bean
    public ErrorDecoder remoteErrorDecoder(ObjectMapper mapper) {
        ErrorDecoder fallback = new ErrorDecoder.Default();
        return (methodKey, response) -> {
            RemoteError body = read(mapper, response);
            if (body != null && body.code() != null) {
                try {
                    return new BusinessException(ErrorCode.valueOf(body.code()), body.message());
                } catch (IllegalArgumentException ignored) {
                    // ma loi khong thuoc ErrorCode (vd METHOD_NOT_ALLOWED) -> xu ly nhu loi ha tang
                }
            }
            log.warn("Remote call {} failed with HTTP {}", methodKey, response.status());
            return fallback.decode(methodKey, response);
        };
    }

    private static RemoteError read(ObjectMapper mapper, Response response) {
        if (response.body() == null) return null;
        try (InputStream in = response.body().asInputStream()) {
            return mapper.readValue(in, RemoteError.class);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RemoteError(String code, String message) {}
}
