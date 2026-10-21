package org.example.ride_hailing_logistics.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * Moi nguong nghiep vu doc tu day (NFR-MNT-005): ban kinh, timeout offer, do moi vi tri, bang gia phu...
 * Gia tri mac dinh nam trong application.properties, khong nam trong code.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Security security,
        RateLimit rateLimit,
        Location location,
        MapProvider map,
        List<Region> regions,
        Pricing pricing,
        Dispatch dispatch,
        Trip trip,
        Payment payment,
        Outbox outbox,
        Storage storage) {

    public record Security(Jwt jwt, Login login, Duration passwordResetTtl) {}

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {}

    public record Login(int maxFailedAttempts, Duration lockDuration) {}

    public record RateLimit(boolean enabled, int loginPerMinute, int telemetryPerMinute, int quotePerMinute,
                            int tripPerMinute, int offerPerMinute, int paymentPerMinute, int uploadPerMinute,
                            int defaultPerMinute) {}

    public record Location(Duration freshness, double maxAccuracyForMatchingMeters, double maxAccuracyMeters,
                           Duration maxFutureSkew, Duration maxAge, double maxSpeedMps, Duration recommendedInterval) {}

    public record MapProvider(String provider, double roadFactor, double averageSpeedKmh, String osrmUrl, Duration timeout) {}

    public record Region(String code, double minLat, double maxLat, double minLng, double maxLng) {
        public boolean contains(double lat, double lng) {
            return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
        }
    }

    public record Pricing(Duration quoteTtl, BigDecimal roundingStep, String currency, Surge surge) {}

    public record Surge(String version, int geohashPrecision, Duration window, BigDecimal sensitivity,
                        BigDecimal min, BigDecimal max) {}

    public record Dispatch(List<Double> radiiKm, Duration offerTimeout, Duration matchingTimeout,
                           Duration retryInterval, int maxCandidates, String rankVersion) {}

    public record Trip(boolean requireStartPin, double arrivalRadiusMeters, Duration realtimeGracePeriod,
                       Duration freeCancelWindow, Duration noShowWait, BigDecimal cancelFeeAfterAccept,
                       BigDecimal cancelFeeArrived, double maxPackageWeightMotorbikeKg, double maxPackageWeightVanKg) {}

    public record Payment(Sandbox sandbox, Duration callbackTolerance, int maxAttempts) {}

    public record Sandbox(String secret, Duration callbackDelay, boolean forceFailure) {}

    public record Outbox(Duration pollInterval, int batchSize, int maxAttempts) {}

    public record Storage(String documentsDir) {}
}
