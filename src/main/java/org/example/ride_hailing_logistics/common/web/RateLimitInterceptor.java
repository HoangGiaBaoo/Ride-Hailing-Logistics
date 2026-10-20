package org.example.ride_hailing_logistics.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.util.List;

/**
 * Gioi han tan suat theo cua so co dinh 1 phut tren Redis (NFR-SEC-007).
 * Khoa theo user neu da dang nhap, theo IP neu chua — de 100 tai xe mo phong tu mot may van khong chan nhau.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    // INCR va dat TTL trong mot lenh nguyen tu: tranh khoa khong bao gio het han neu tien trinh chet giua hai lenh
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end return c",
            Long.class);

    private final StringRedisTemplate redis;
    private final AppProperties props;
    private final Clock clock;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AppProperties.RateLimit limits = props.rateLimit();
        if (!limits.enabled() || !request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        Bucket bucket = classify(request, limits);
        String subject = subject(request);
        long window = clock.millis() / 60_000;
        String key = "rl:" + bucket.name + ":" + subject + ":" + window;
        try {
            Long count = redis.execute(INCREMENT, List.of(key), "70");
            if (count != null && count > bucket.limit) {
                response.setHeader("Retry-After", String.valueOf(60 - (clock.millis() / 1000) % 60));
                throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED, "Bạn thao tác quá nhanh, vui lòng thử lại sau ít giây.");
            }
        } catch (DataAccessException e) {
            // Redis loi thi khong chan nguoi dung vi rate limit; cac phan phu thuoc Redis khac se bao loi rieng
            log.warn("Rate limit skipped, Redis unavailable: {}", e.getMessage());
        }
        return true;
    }

    private Bucket classify(HttpServletRequest request, AppProperties.RateLimit l) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if (uri.startsWith("/api/v1/auth/")) return new Bucket("auth", l.loginPerMinute());
        if (uri.endsWith("/location")) return new Bucket("telemetry", l.telemetryPerMinute());
        if (uri.startsWith("/api/v1/quotes")) return new Bucket("quote", l.quotePerMinute());
        if (uri.startsWith("/api/v1/offers/")) return new Bucket("offer", l.offerPerMinute());
        if (uri.startsWith("/api/v1/payments/")) return new Bucket("payment", l.paymentPerMinute());
        if (uri.endsWith("/documents") && "POST".equals(method)) return new Bucket("upload", l.uploadPerMinute());
        if (uri.equals("/api/v1/trips") && "POST".equals(method)) return new Bucket("trip", l.tripPerMinute());
        return new Bucket("default", l.defaultPerMinute());
    }

    private String subject(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return "u:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private record Bucket(String name, int limit) {}
}
