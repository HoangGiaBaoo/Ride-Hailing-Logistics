package org.example.ride_hailing_logistics.realtime;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** DriverEarningPosted (payment-service) -> bao tai xe thu nhap chuyen (SRS §5.6, consumer: Driver App). */
@Component
@RequiredArgsConstructor
class EarningNotificationHandler implements EventHandler {

    private final RealtimePublisher publisher;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "realtime.driver-earning";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("DriverEarningPosted");
    }

    @Override
    public void handle(EventEnvelope event) {
        EarningPostedPayload payload = outbox.read(event, EarningPostedPayload.class);
        publisher.toUser(payload.driverId(), RealtimePublisher.QUEUE_NOTIFICATIONS, "DRIVER_EARNING_POSTED", payload);
    }

    /** Hop dong voi payment-service (event DriverEarningPosted v1); them truong o cuoi, khong doi nghia truong cu. */
    record EarningPostedPayload(UUID tripId, UUID walletId, UUID driverId, BigDecimal netEarning, BigDecimal gross,
                                BigDecimal commission, int commissionRuleVersion, BigDecimal balance, String currency,
                                Instant postedAt) {}
}
