package org.example.ride_hailing_logistics.trip;

import org.example.ride_hailing_logistics.IntegrationTestSupport;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.driver.DriverEnums.DriverAvailability;
import org.example.ride_hailing_logistics.payment.Payment;
import org.example.ride_hailing_logistics.payment.PaymentService;
import org.example.ride_hailing_logistics.trip.TripDtos.TransitionRequest;
import org.example.ride_hailing_logistics.trip.TripDtos.TripResponse;
import org.example.ride_hailing_logistics.trip.dispatch.DispatchService;
import org.example.ride_hailing_logistics.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** UC-04 den cuoi: hoan tat -> chot cuoc -> thanh toan -> vi, va moi buoc tai chinh chi xay ra mot lan (BR-009, BR-015). */
class TripLifecycleIT extends IntegrationTestSupport {

    @Autowired
    private PaymentService paymentService;

    @Test
    void completedCashTripPostsCommissionExactlyOnce() throws Exception {
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
        assertEquals(DriverAvailability.AVAILABLE, driverAvailability.availabilityOf(driverId));

        // Relay outbox: TripCompleted -> FareFinalized -> Payment (CASH thanh cong ngay) -> PaymentSucceeded -> vi
        Integer entries = await(() -> {
            Integer n = jdbc.queryForObject("select count(*) from wallet_entries", Integer.class);
            return n != null && n > 0 ? n : null;
        }, 15_000);
        assertNotNull(entries, "wallet was never credited");
        assertEquals(1, jdbc.queryForObject("select count(*) from outbox_events where event_type = 'TripCompleted'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from payments where trip_id = ?", Integer.class, trip.id()));

        // Hoa hong 20% cua cuoc da bao gia; tien mat -> chi ghi no hoa hong
        BigDecimal fare = trip.fare().quotedTotal();
        BigDecimal balance = walletService.walletOf(driverId).balance();
        assertEquals(0, fare.multiply(new BigDecimal("0.20")).negate().compareTo(balance), "balance " + balance);

        // Consumer chay lai (vi du admin xu ly lai event): khong ghi trung (FR-WAL-005, FR-PAY-003)
        UUID paymentId = jdbc.queryForObject("select id from payments where trip_id = ?", UUID.class, trip.id());
        paymentService.createForTrip(trip.id(), trip.code(), Payment.Kind.TRIP_FARE, customer, driverId, ServiceType.RIDE,
                fare, "VND", PaymentMethod.CASH);
        walletService.postPaymentEarnings(new WalletService.PaymentSucceededView(paymentId, trip.id(), trip.code(),
                "TRIP_FARE", driverId, ServiceType.RIDE, fare, "VND", "CASH", Instant.now()));
        assertEquals(1, jdbc.queryForObject("select count(*) from payments where trip_id = ?", Integer.class, trip.id()));
        assertEquals(entries, jdbc.queryForObject("select count(*) from wallet_entries", Integer.class));
        assertEquals(0, balance.compareTo(walletService.walletOf(driverId).balance()));
    }

    @Test
    void customerCancellingWhileMatchingReleasesTheOfferedDriver() throws Exception {
        UUID customer = newCustomer();
        UUID driverId = onlineDriverAt(10.7772, 106.7012);
        TripResponse trip = requestRide(customer);
        assertNotNull(await(() -> dispatchService.currentOfferFor(driverId).orElse(null), 10_000));
        assertEquals(DriverAvailability.OFFERED, driverAvailability.availabilityOf(driverId));

        Actor customerActor = new Actor(customer, Set.of(Role.CUSTOMER));
        tripService.cancel(trip.id(), customerActor, CancelReason.CHANGED_PLANS, null, false);
        tripService.cancel(trip.id(), customerActor, CancelReason.CHANGED_PLANS, null, false);   // huy lap: idempotent

        // FR-CAN-005, FR-DRV-013: offer bi huy va tai xe tro lai AVAILABLE dung mot lan, khong ket OFFERED
        assertEquals(DriverAvailability.AVAILABLE, driverAvailability.availabilityOf(driverId));
        assertEquals(0, jdbc.queryForObject("select count(*) from driver_offers where status = 'PENDING'", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from trip_status_history where trip_id = ? and to_status = 'CANCELLED'",
                Integer.class, trip.id()));
    }
}
