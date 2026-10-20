package org.example.ride_hailing_logistics.trip;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Read model "tai xe -> chuyen dang chay" tren Redis, de moi ban tin GPS biet gui cho ai ma khong truy van DB.
 * DB van la nguon su that; read model tai tao duoc khi khoi dong (DR-003).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveTripRegistry {

    private static final String KEY = "trip:active-by-driver";

    private final StringRedisTemplate redis;
    private final TripRepository trips;

    public void put(UUID driverId, UUID tripId) {
        redis.opsForHash().put(KEY, driverId.toString(), tripId.toString());
    }

    public void remove(UUID driverId) {
        redis.opsForHash().delete(KEY, driverId.toString());
    }

    public Optional<UUID> tripOf(UUID driverId) {
        Object v = redis.opsForHash().get(KEY, driverId.toString());
        return v == null ? Optional.empty() : Optional.of(UUID.fromString(v.toString()));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuild() {
        try {
            redis.delete(KEY);
            trips.findByStatusIn(TripStatus.DRIVER_ACTIVE).forEach(t -> put(t.getDriverId(), t.getId()));
        } catch (DataAccessException e) {
            log.warn("Could not rebuild active trip registry: {}", e.getMessage());
        }
    }
}
