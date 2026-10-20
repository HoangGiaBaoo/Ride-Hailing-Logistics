package org.example.ride_hailing_logistics.realtime;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.wallet.WalletService;
import org.example.ride_hailing_logistics.wallet.WalletService.EarningPostedPayload;
import org.springframework.stereotype.Component;

import java.util.Set;

/** DriverEarningPosted -> bao tai xe thu nhap chuyen (SRS §5.6, consumer: Driver App). */
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
        return Set.of(WalletService.EVENT_EARNING_POSTED);
    }

    @Override
    public void handle(EventEnvelope event) {
        EarningPostedPayload payload = outbox.read(event, EarningPostedPayload.class);
        publisher.toUser(payload.driverId(), RealtimePublisher.QUEUE_NOTIFICATIONS, "DRIVER_EARNING_POSTED", payload);
    }
}
