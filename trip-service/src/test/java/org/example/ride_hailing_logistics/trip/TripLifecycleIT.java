package org.example.ride_hailing_logistics.trip;

import org.example.ride_hailing_logistics.InMemoryDriverPool.State;
import org.example.ride_hailing_logistics.IntegrationTestSupport;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.trip.TripDtos.TransitionRequest;
import org.example.ride_hailing_logistics.trip.TripDtos.TripResponse;
import org.example.ride_hailing_logistics.trip.dispatch.DispatchService;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * UC-04 trong pham vi trip-service: hoan tat phat TripCompleted dung MOT lan (phan tai chinh phia sau:
 * PaymentLedgerIT cua payment-service), va huy chuyen tra tai xe ve AVAILABLE dung mot lan.
 */
class TripLifecycleIT extends IntegrationTestSupport {

    @Test
    void completedTripReleasesDriverAndEmitsTripCompletedOnce() throws Exception {
        UUID customer = newCustomer();
        UUID driverId = onlineDriverAt(10.7772, 106.7012);
        TripResponse trip = requestRide(customer);
        DispatchService.OfferView offer = await(() -> dispatchService.currentOfferFor(driverId).orElse(null), 10_000);
        assertNotNull(offer);
        dispatchService.accept(offer.offerId(), driverId);

        Actor driver = new Actor(driverId, Set.of(Role.DRIVER));
        Actor customerActor = new Actor(customer, Set.of(Role.CUSTOMER));
        String pin = tripService.get(trip.id(), customerActor).startPin();
        tripService.startPickup(trip.id(), driver, null);
        tripService.arrive(trip.id(), driver, new TransitionRequest(10.7770, 106.7010, null, null));
        tripService.start(trip.id(), driver, new TransitionRequest(null, null, pin, null));
        tripService.complete(trip.id(), driver, null);
        tripService.complete(trip.id(), driver, null);   // gui lap: khong phat TripCompleted lan hai

        assertEquals(State.AVAILABLE, drivers.stateOf(driverId));
        assertEquals(1, count("select count(*) from outbox_events where event_type = 'TripCompleted'"));
        assertEquals(1, count("select count(*) from trip_status_history where trip_id = ? and to_status = 'COMPLETED'", trip.id()));
        // Moi buoc cua state machine deu co dung mot dong lich su (FR-TRIP-008)
        assertEquals(7, count("select count(*) from trip_status_history where trip_id = ?", trip.id()));
    }

    @Test
    void customerCancellingWhileMatchingReleasesTheOfferedDriver() throws Exception {
        UUID customer = newCustomer();
        UUID driverId = onlineDriverAt(10.7772, 106.7012);
        TripResponse trip = requestRide(customer);
        assertNotNull(await(() -> dispatchService.currentOfferFor(driverId).orElse(null), 10_000));
        assertEquals(State.OFFERED, drivers.stateOf(driverId));

        Actor customerActor = new Actor(customer, Set.of(Role.CUSTOMER));
        tripService.cancel(trip.id(), customerActor, CancelReason.CHANGED_PLANS, null, false);
        tripService.cancel(trip.id(), customerActor, CancelReason.CHANGED_PLANS, null, false);   // huy lap: idempotent

        // FR-CAN-005, FR-DRV-013: offer bi huy va tai xe tro lai AVAILABLE dung mot lan, khong ket OFFERED
        assertEquals(State.AVAILABLE, drivers.stateOf(driverId));
        assertEquals(0, count("select count(*) from driver_offers where status = 'PENDING'"));
        assertEquals(1, count("select count(*) from trip_status_history where trip_id = ? and to_status = 'CANCELLED'", trip.id()));
        assertEquals(1, count("select count(*) from outbox_events where event_type = 'TripCancelled'"));
    }
}
