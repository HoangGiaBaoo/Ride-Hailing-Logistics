package org.example.ride_hailing_logistics.common.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import feign.RequestInterceptor;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
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
 * - Resilience4j: circuit breaker cho moi Feign client (bo qua loi 4xx nghiep vu), chi thu lai GET
 *   voi backoff tang dan (POST thu lai co the tao hieu ung trung).
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
            int status = response.status();
            if (status == 502 || status == 503 || status == 504) {
                // Danh dau thu lai duoc; GetOnlyRetryer quyet dinh co thu lai hay khong theo method
                return new RetryableException(status, "Remote service unavailable", response.request().httpMethod(),
                        (Long) null, response.request());
            }
            return fallback.decode(methodKey, response);
        };
    }

    @Bean
    public Retryer getOnlyRetryer() {
        return new GetOnlyRetryer(100, 1_000, 3);
    }

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreaker() {
        CircuitBreakerConfig circuitBreaker = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .slowCallRateThreshold(80)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                // 404/409/422 la cau tra loi dung cua service dich, khong phai dau hieu service hong
                .ignoreException(FeignSupport::isClientError)
                .build();
        TimeLimiterConfig timeLimiter = TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(6)).build();
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(circuitBreaker).timeLimiterConfig(timeLimiter).build());
    }

    static boolean isClientError(Throwable e) {
        return e instanceof BusinessException b && b.getCode().getStatus().is4xxClientError();
    }

    /** Thu lai voi khoang cho nhan doi (100ms, 200ms, 400ms...), chi cho GET. */
    static final class GetOnlyRetryer implements Retryer {

        private final long period;
        private final long maxPeriod;
        private final int maxAttempts;
        private int attempt = 1;

        GetOnlyRetryer(long period, long maxPeriod, int maxAttempts) {
            this.period = period;
            this.maxPeriod = maxPeriod;
            this.maxAttempts = maxAttempts;
        }

        @Override
        public void continueOrPropagate(RetryableException e) {
            if (e.method() != Request.HttpMethod.GET || attempt++ >= maxAttempts) {
                throw e;
            }
            long interval = Math.min(maxPeriod, period * (1L << (attempt - 2)));
            try {
                Thread.sleep(interval);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        @Override
        public Retryer clone() {
            // Feign goi clone() cho moi request: moi request co bo dem rieng
            return new GetOnlyRetryer(period, maxPeriod, maxAttempts);
        }
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
