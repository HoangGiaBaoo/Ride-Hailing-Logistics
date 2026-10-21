package org.example.ride_hailing_logistics.location;

import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chi muc khong gian tren Redis GEO (CON-04). Day la du lieu DAN XUAT tu telemetry hop le,
 * mat thi tai tao lai duoc tu ban tin tiep theo (DR-GEO-005).
 *
 * CHU Y: Redis nhan (longitude, latitude) — nguoc thu tu thuong dung.
 */
@Component
@RequiredArgsConstructor
public class DriverLocationIndex {

    static final String LAST_SEEN_KEY = "geo:lastseen";

    /**
     * So sequence va ghi trong MOT script de khong co khe ho giua doc va ghi (FR-LOC-006, NFR-REL-002).
     * Ban tin co sequence <= sequence da luu bi bo qua, tru khi lan cap nhat truoc da qua cu
     * (app khoi dong lai thi sequence dem lai tu dau).
     */
    private static final DefaultRedisScript<Long> UPSERT = new DefaultRedisScript<>("""
            local prevSeq = tonumber(redis.call('HGET', KEYS[1], 'seq'))
            local prevServer = tonumber(redis.call('HGET', KEYS[1], 'serverTs'))
            local seq = tonumber(ARGV[1])
            local serverTs = tonumber(ARGV[6])
            if prevSeq and seq <= prevSeq and prevServer and (serverTs - prevServer) < tonumber(ARGV[12]) then
              return 0
            end
            local prevType = redis.call('HGET', KEYS[1], 'vehicleType')
            if prevType and prevType ~= ARGV[10] then
              redis.call('ZREM', 'geo:drivers:' .. prevType, ARGV[5])
            end
            redis.call('HSET', KEYS[1], 'seq', ARGV[1], 'deviceTs', ARGV[2], 'lng', ARGV[3], 'lat', ARGV[4],
                       'serverTs', ARGV[6], 'acc', ARGV[7], 'heading', ARGV[8], 'speed', ARGV[9],
                       'vehicleType', ARGV[10], 'suspicious', ARGV[11])
            redis.call('GEOADD', KEYS[2], ARGV[3], ARGV[4], ARGV[5])
            redis.call('ZADD', KEYS[3], serverTs, ARGV[5])
            return 1
            """, Long.class);

    private static final long SEQUENCE_RESET_WINDOW_MS = 60_000;

    private final StringRedisTemplate redis;

    /** @return true neu ban tin duoc ghi; false neu cu hon ban da luu. */
    public boolean upsert(UUID driverId, String vehicleType, Telemetry t, boolean suspicious, Instant serverNow) {
        String id = driverId.toString();
        Long written = redis.execute(UPSERT, List.of(hashKey(driverId), geoKey(vehicleType), LAST_SEEN_KEY),
                String.valueOf(t.sequence()), String.valueOf(t.deviceTimestamp().toEpochMilli()),
                String.valueOf(t.longitude()), String.valueOf(t.latitude()), id,
                String.valueOf(serverNow.toEpochMilli()), String.valueOf(t.accuracyMeters()),
                t.headingDegrees() == null ? "" : String.valueOf(t.headingDegrees()),
                t.speedMetersPerSecond() == null ? "" : String.valueOf(t.speedMetersPerSecond()),
                vehicleType, suspicious ? "1" : "0", String.valueOf(SEQUENCE_RESET_WINDOW_MS));
        return written != null && written == 1;
    }

    public Optional<LatestLocation> get(UUID driverId) {
        Map<Object, Object> h = redis.opsForHash().entries(hashKey(driverId));
        return h.isEmpty() ? Optional.empty() : Optional.of(toLocation(driverId, h));
    }

    /** Ung vien trong ban kinh, sap theo khoang cach tang dan. */
    public List<Hit> search(double lat, double lng, double radiusMeters, String vehicleType, int limit) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().search(geoKey(vehicleType),
                GeoReference.fromCoordinate(lng, lat), new Distance(radiusMeters / 1000.0, Metrics.KILOMETERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().sortAscending().limit(limit));
        List<Hit> hits = new ArrayList<>();
        if (results == null) return hits;
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results) {
            hits.add(new Hit(UUID.fromString(r.getContent().getName()), r.getDistance().getValue() * 1000.0));
        }
        return hits;
    }

    public void remove(UUID driverId) {
        String id = driverId.toString();
        Object type = redis.opsForHash().get(hashKey(driverId), "vehicleType");
        if (type != null) {
            redis.opsForZSet().remove(geoKey(type.toString()), id);
        }
        redis.opsForZSet().remove(LAST_SEEN_KEY, id);
        redis.delete(hashKey(driverId));
    }

    /** Tai xe khong gui vi tri qua lau: xoa khoi chi muc (FR-LOC-008). */
    public int removeStale(Instant olderThan, int max) {
        var stale = redis.opsForZSet().rangeByScore(LAST_SEEN_KEY, 0, olderThan.toEpochMilli(), 0, max);
        if (stale == null) return 0;
        stale.forEach(id -> remove(UUID.fromString(id)));
        return stale.size();
    }

    private static LatestLocation toLocation(UUID driverId, Map<Object, Object> h) {
        return new LatestLocation(driverId, dbl(h.get("lat")), dbl(h.get("lng")), dbl(h.get("acc")),
                optDbl(h.get("heading")), optDbl(h.get("speed")),
                Instant.ofEpochMilli(Long.parseLong(h.get("deviceTs").toString())),
                Instant.ofEpochMilli(Long.parseLong(h.get("serverTs").toString())),
                Long.parseLong(h.get("seq").toString()), String.valueOf(h.get("vehicleType")),
                "1".equals(String.valueOf(h.get("suspicious"))));
    }

    private static double dbl(Object v) {
        return Double.parseDouble(v.toString());
    }

    private static Double optDbl(Object v) {
        return v == null || v.toString().isEmpty() ? null : Double.parseDouble(v.toString());
    }

    static String hashKey(UUID driverId) {
        return "loc:" + driverId;
    }

    static String geoKey(String vehicleType) {
        return "geo:drivers:" + vehicleType;
    }

    public record Hit(UUID driverId, double distanceMeters) {}
}
