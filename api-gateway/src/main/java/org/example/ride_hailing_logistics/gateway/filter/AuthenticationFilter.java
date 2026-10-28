package org.example.ride_hailing_logistics.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.gateway.config.GatewayProperties;
import org.example.ride_hailing_logistics.gateway.security.ReactiveTokenRevocationChecker;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * Xac thuc tap trung cho /api/** (NFR-SEC-004):
 * 1. Xoa X-User-Id / X-User-Roles client tu gui — chong gia mao danh tinh (NFR-SEC-012)
 * 2. Duong mo (dang nhap, callback thanh toan...) cho qua; con lai bat buoc Bearer token
 * 3. Kiem chu ky, han, issuer; tu choi token SERVICE tu ben ngoai
 * 4. Kiem thu hoi tren Redis (dang xuat -> blacklist jti; doi mat khau/khoa -> token version)
 * 5. Gan X-User-Id, X-User-Roles cho service phia sau
 * Service van tu kiem JWT + quyen so huu (phong thu nhieu lop): gateway chi chan som, khong thay the.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final String SERVICE_SUBJECT_PREFIX = "service:";

    private final ReactiveJwtDecoder jwtDecoder;
    private final ReactiveTokenRevocationChecker revocationChecker;
    private final GatewayProperties props;
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange stripped = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove(USER_ID_HEADER);
                    h.remove(USER_ROLES_HEADER);
                }))
                .build();
        String path = stripped.getRequest().getPath().value();
        // Trang tinh va /ws khong qua day: /ws xac thuc o frame STOMP CONNECT (NFR-SEC-005)
        if (!path.startsWith("/api/") || isPublic(path)) {
            return chain.filter(stripped);
        }
        String header = stripped.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(stripped);
        }
        return jwtDecoder.decode(header.substring(7))
                .filter(jwt -> jwt.getSubject() != null && !jwt.getSubject().startsWith(SERVICE_SUBJECT_PREFIX))
                .filterWhen(revocationChecker::isActive)
                .flatMap(jwt -> chain.filter(withIdentity(stripped, jwt)))
                .switchIfEmpty(Mono.defer(() -> unauthorized(stripped)))
                .onErrorResume(JwtException.class, e -> {
                    log.debug("Rejected token on {}: {}", path, e.getMessage());
                    return unauthorized(stripped);
                });
    }

    private boolean isPublic(String path) {
        return props.getPublicPaths().stream().anyMatch(p -> matcher.match(p, path));
    }

    private static ServerWebExchange withIdentity(ServerWebExchange exchange, Jwt jwt) {
        String roles = String.join(",", rolesOf(jwt));
        return exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.set(USER_ID_HEADER, jwt.getSubject());
                    h.set(USER_ROLES_HEADER, roles);
                }))
                .build();
    }

    /** Cung dinh dang ApiResponse cua service (Phu luc B); khong noi ly do cu the (NFR-USA-005). */
    private static Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String correlationId = exchange.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        String body = "{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"Phiên đăng nhập không hợp lệ hoặc đã hết hạn. "
                + "Vui lòng đăng nhập lại.\"" + (correlationId == null ? "" : ",\"correlationId\":\"" + correlationId + "\"") + "}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    static List<String> rolesOf(Jwt jwt) {
        Object raw = jwt.getClaim("roles");
        return raw instanceof Collection<?> c ? c.stream().map(String::valueOf).toList() : List.of();
    }
}
