package org.example.ride_hailing_logistics.trip.websocket;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.trip.service.TripQueries;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kiem quyen moi frame STOMP o server (FR-IAM-010, NFR-SEC-005, NFR-PRV-002):
 * - CONNECT: bat buoc JWT hop le; luu han token de buoc ket noi lai khi het han (COM-005)
 * - SUBSCRIBE: chi kenh rieng cua minh, hoac kenh chuyen ma minh tham gia
 * - SEND: chi tai xe duoc gui telemetry
 */
@Slf4j
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern TRIP_TOPIC = Pattern.compile("^/topic/trips/([0-9a-fA-F-]{36})$");
    private static final String EXPIRES_AT = "tokenExpiresAt";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtConverter;
    private final TripQueries tripQueries;
    private final Clock clock;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtConverter,
                                @Lazy TripQueries tripQueries, Clock clock) {
        this.jwtDecoder = jwtDecoder;
        this.jwtConverter = jwtConverter;
        this.tripQueries = tripQueries;
        this.clock = clock;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            default -> {
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("Missing bearer token");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));
            Authentication auth = jwtConverter.convert(jwt);
            accessor.setUser(auth);
            Map<String, Object> attributes = accessor.getSessionAttributes();
            if (attributes != null) {
                attributes.put(EXPIRES_AT, jwt.getExpiresAt());
            }
        } catch (JwtException e) {
            throw new AccessDeniedException("Invalid token");
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Actor actor = requireActor(accessor);
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new AccessDeniedException("Missing destination");
        }
        if (destination.startsWith("/user/queue/")) {
            return;   // Spring tu dinh tuyen /user/queue/x ve dung session cua chinh nguoi do
        }
        Matcher m = TRIP_TOPIC.matcher(destination);
        if (m.matches() && tripQueries.canSubscribe(UUID.fromString(m.group(1)), actor)) {
            return;
        }
        log.warn("Subscription to {} denied for user {}", destination, actor.id());
        throw new AccessDeniedException("Subscription denied");
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        Actor actor = requireActor(accessor);
        if ("/app/driver/location".equals(accessor.getDestination()) && actor.has(Role.DRIVER)) {
            return;
        }
        throw new AccessDeniedException("Send denied");
    }

    private Actor requireActor(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication auth)) {
            throw new AccessDeniedException("Not authenticated");
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object expiresAt = attributes == null ? null : attributes.get(EXPIRES_AT);
        if (expiresAt instanceof Instant exp && !exp.isAfter(clock.instant())) {
            // Token het han giua phien: client phai ket noi lai voi token moi (COM-005)
            throw new AccessDeniedException("Token expired");
        }
        return new Actor(CurrentUser.idOf(auth), CurrentUser.rolesOf(auth));
    }
}
