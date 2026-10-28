package org.example.ride_hailing_logistics.pricing.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.util.GeoUtils;
import org.example.ride_hailing_logistics.pricing.client.LocationClient;
import org.example.ride_hailing_logistics.pricing.domain.SurgeCalculator;
import org.example.ride_hailing_logistics.pricing.dto.SurgeQuote;
import org.example.ride_hailing_logistics.pricing.service.SurgeService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Cau: so yeu cau chuyen hop le trong o, dem theo tung phut tren Redis, cong N phut gan nhat.
 * Cung: so tai xe AVAILABLE co vi tri moi trong o, hoi location-service qua Feign.
 */
@Slf4j
@Service
public class SurgeServiceImpl implements SurgeService {

    private final StringRedisTemplate redis;
    private final LocationClient locationClient;
    private final AppProperties.Surge props;
    private final Clock clock;

    public SurgeServiceImpl(StringRedisTemplate redis, LocationClient locationClient, AppProperties appProperties,
                            Clock clock) {
        this.redis = redis;
        this.locationClient = locationClient;
        this.props = appProperties.pricing().surge();
        this.clock = clock;
    }

    @Override
    public String cellOf(double lat, double lng) {
        return GeoUtils.geohash(lat, lng, props.geohashPrecision());
    }

    @Override
    public void recordDemand(double lat, double lng) {
        String key = demandKey(cellOf(lat, lng), currentMinute());
        redis.opsForValue().increment(key);
        redis.expire(key, props.window().plus(Duration.ofMinutes(5)));
    }

    @Override
    public SurgeQuote current(String cell) {
        try {
            long demand = demand(cell);
            long supply = locationClient.supply(cell).data().availableDrivers();
            BigDecimal multiplier = SurgeCalculator.multiplier(demand, supply, props.sensitivity(), props.min(), props.max());
            return new SurgeQuote(multiplier, props.version(), cell, demand, supply);
        } catch (DataAccessException | BusinessException e) {
            // Khong do duoc cung/cau thi khong tang gia: an toan cho khach hon la doan surge
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
        return values.stream().filter(Objects::nonNull).mapToLong(Long::parseLong).sum();
    }

    private long currentMinute() {
        return clock.millis() / 60_000;
    }

    private static String demandKey(String cell, long minute) {
        return "surge:demand:" + cell + ":" + minute;
    }
}
