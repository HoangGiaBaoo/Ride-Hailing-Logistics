package org.example.ride_hailing_logistics.trip.dispatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.web.CorrelationId;
import org.example.ride_hailing_logistics.trip.TripLocalEvents;
import org.example.ride_hailing_logistics.trip.TripRepository;
import org.example.ride_hailing_logistics.trip.TripStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.UUID;

/**
 * Nhip tim cua dispatch:
 * - ngay sau commit tao chuyen / tu choi / het han -> dispatch lien (do tre thap)
 * - moi giay quet offer het han va chuyen den hen dispatch lai (luoi an toan khi tien trinh khoi dong lai, NFR-REL-008)
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DispatchScheduler {

    private final DispatchService dispatchService;
    private final DriverOfferRepository offers;
    private final TripRepository trips;
    private final Clock clock;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDispatchRequested(TripLocalEvents.DispatchRequested event) {
        safely("dispatch " + event.tripId(), () -> dispatchService.dispatchNext(event.tripId()));
    }

    @Scheduled(fixedDelay = 1000)
    public void expireOffers() {
        for (UUID offerId : offers.findExpired(DriverOffer.Status.PENDING, clock.instant())) {
            safely("expire offer " + offerId, () -> dispatchService.expire(offerId));
        }
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 500)
    public void dispatchDueTrips() {
        for (UUID tripId : trips.findDueForDispatch(TripStatus.MATCHING, clock.instant())) {
            safely("dispatch " + tripId, () -> dispatchService.dispatchNext(tripId));
        }
    }

    private void safely(String what, Runnable work) {
        CorrelationId.with(null, () -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                // Mot chuyen loi khong duoc chan ca vong quet; lan quet sau se thu lai
                log.warn("Failed to {}: {}", what, e.toString());
            }
            return null;
        });
    }
}
