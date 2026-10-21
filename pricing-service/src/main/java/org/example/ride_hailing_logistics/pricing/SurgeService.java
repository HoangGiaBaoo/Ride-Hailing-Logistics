package org.example.ride_hailing_logistics.pricing;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.util.GeoUtils;
import org.example.ride_hailing_logistics.pricing.client.DriverClient;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Do cung/cau theo o geohash va cua so truot (FR-PRI-007, FR-PRI-008).
 * Cau: so yeu cau chuyen hop le trong o, dem theo tung phut, cong N phut gan nhat.
 * Cung: so tai xe AVAILABLE co vi tri moi trong o.
 */
@Slf4j
@Service
public class SurgeService {

    private final StringRedisTemplate redis;
    private final DriverClient driverClient;
    private final AppProperties.Surge props;
    private final Clock clock;

    public SurgeService(StringRedisTemplate redis, DriverClient driverClient, AppProperties appProperties, Clock clock) {
        this.redis = redis;
        this.driverClient = driverClient;
        this.props = appProperties.pricing().surge();
        this.clock = clock;
    }

    public String cellOf(double lat, double lng) {
        return GeoUtils.geohash(lat, lng, props.geohashPrecision());
    }

    public void recordDemand(double lat, double lng) {
        String key = demandKey(cellOf(lat, lng), currentMinute());
        redis.opsForValue().increment(key);
        redis.expire(key, props.window().plus(Duration.ofMinutes(5)));
    }

    public SurgeQuote current(String cell) {
        try {
            long demand = demand(cell);
            long supply = driverClient.supply(cell).data();
            BigDecimal multiplier = SurgeCalculator.multiplier(demand, supply, props.sensitivity(), props.min(), props.max());
            return new SurgeQuote(multiplier, props.version(), cell, demand, supply);
        } catch (DataAccessException | feign.FeignException | org.example.ride_hailing_logistics.common.error.BusinessException e) {
            // Khong do duoc cung/cau (Redis/driver-service loi) thi khong tang gia: an toan cho khach hon la doan surge
            log.warn("Surge unavailable for cell {}, using 1.0: {}", cell, e.getMessage());
            return new SurgeQuote(BigDecimal.ONE.setScale(1), props.version(), cell, 0, 0);
        }
    }

    private long demand(String cell) {
        long minute = currentMinute();
        long minutes = Math.max(1, props.window().toMinutes());
        List<String> keys = new ArrayList<>();
        for (long m = minute - minutes + 1; m <= minute; m++) {
            keys.add(demandKey(cell, m));
        }
        List<String> values = redis.opsForValue().multiGet(keys);
        if (values == null) return 0;
        return values.stream().filter(v -> v != null).mapToLong(Long::parseLong).sum();
    }

    private long currentMinute() {
        return clock.millis() / 60_000;
    }

    private static String demandKey(String cell, long minute) {
        return "surge:demand:" + cell + ":" + minute;
    }

    public record SurgeQuote(BigDecimal multiplier, String version, String cell, long demand, long supply) {}
}
