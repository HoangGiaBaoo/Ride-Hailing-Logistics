package org.example.ride_hailing_logistics.driver;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.driver.DriverEnums.DriverAvailability;
import org.example.ride_hailing_logistics.driver.client.TripClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Luoi an toan cho giao dich phan tan giua trip-service va driver-service.
 * Neu trip-service chet giua "giu tai xe" va "commit offer/chuyen", hoac lenh tra tai xe bi mat,
 * tai xe co the ket o OFFERED/BUSY. Job nay hoi trip-service (nguon su that ve gan chuyen) va tra tai xe
 * neu offer/chuyen dang giu khong con hieu luc (FR-DRV-013, NFR-REL-007).
 */
@Slf4j
@Component
public class DriverHoldReconciler {

    private static final Duration BUSY_CHECK_AFTER = Duration.ofMinutes(2);
    private static final int BATCH = 50;

    private final DriverProfileRepository profiles;
    private final TripClient tripClient;
    private final TransactionTemplate tx;
    private final Duration offeredCheckAfter;
    private final Clock clock;

    public DriverHoldReconciler(DriverProfileRepository profiles, TripClient tripClient, PlatformTransactionManager txManager,
                                AppProperties appProperties, Clock clock) {
        this.profiles = profiles;
        this.tripClient = tripClient;
        this.tx = new TransactionTemplate(txManager);
        // Offer song toi da offer-timeout; qua them 30 giay ma van OFFERED thi dang ngo
        this.offeredCheckAfter = appProperties.dispatch().offerTimeout().plusSeconds(30);
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void reconcile() {
        Instant now = clock.instant();
        List<DriverProfile> stale = profiles.findStaleHolds(DriverAvailability.OFFERED, now.minus(offeredCheckAfter),
                DriverAvailability.BUSY, now.minus(BUSY_CHECK_AFTER), PageRequest.of(0, BATCH));
        for (DriverProfile p : stale) {
            try {
                check(p.getId(), p.getAvailability(), p.getAvailabilityRef());
            } catch (RuntimeException e) {
                // trip-service khong tra loi: KHONG tu tra tai xe (co the van dang trong chuyen), thu lai lan sau
                log.warn("Cannot reconcile driver {}: {}", p.getId(), e.toString());
            }
        }
    }

    private void check(UUID driverId, DriverAvailability availability, UUID ref) {
        TripClient.Engagement engagement = tripClient.engagement(driverId).data();
        UUID expected = availability == DriverAvailability.OFFERED ? engagement.pendingOfferId() : engagement.activeTripId();
        Instant now = clock.instant();
        tx.executeWithoutResult(s -> {
            if (ref != null && Objects.equals(expected, ref)) {
                profiles.touchHold(driverId, ref, now);   // con hieu luc: lui lan kiem sau
            } else if (profiles.releaseHeld(driverId, availability.name(), ref, now) == 1) {
                log.warn("Released driver {} stuck in {} (ref {}, trip-service expects {})", driverId, availability, ref, expected);
            }
        });
    }
}
