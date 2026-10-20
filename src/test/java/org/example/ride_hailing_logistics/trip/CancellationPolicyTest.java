package org.example.ride_hailing_logistics.trip;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tung dong cua bang phi huy cancel-v1 (docs/01-nghiep-vu.md §5), kem bien 2 phut va 5 phut. */
class CancellationPolicyTest {

    private final CancellationPolicy policy = new CancellationPolicy(Duration.ofMinutes(2), Duration.ofMinutes(5),
            BigDecimal.valueOf(10_000), BigDecimal.valueOf(20_000));
    private final Instant accepted = Instant.parse("2026-10-05T08:30:00Z");
    private final Instant arrived = Instant.parse("2026-10-05T08:40:00Z");

    @Test
    void customerCancelsFreeBeforeMatch() {
        assertFee(0, TripActorRole.CUSTOMER, TripStatus.CREATED, null, null, accepted);
        assertFee(0, TripActorRole.CUSTOMER, TripStatus.MATCHING, null, null, accepted);
    }

    @Test
    void customerCancelsFreeWithinTwoMinutesOfMatch() {
        assertFee(0, TripActorRole.CUSTOMER, TripStatus.ACCEPTED, accepted, null, accepted.plusSeconds(119));
        assertFee(0, TripActorRole.CUSTOMER, TripStatus.ACCEPTED, accepted, null, accepted.plusSeconds(120));   // bien: dung 2 phut van mien phi
    }

    @Test
    void customerPaysAfterTwoMinutes() {
        assertFee(10_000, TripActorRole.CUSTOMER, TripStatus.ACCEPTED, accepted, null, accepted.plusSeconds(121));
        assertFee(10_000, TripActorRole.CUSTOMER, TripStatus.PICKING_UP, accepted, null, accepted.plusSeconds(180));
    }

    @Test
    void driverCancellingBeforeArrivalCostsCustomerNothing() {
        assertFee(0, TripActorRole.DRIVER, TripStatus.ACCEPTED, accepted, null, accepted.plusSeconds(600));
        assertFee(0, TripActorRole.DRIVER, TripStatus.PICKING_UP, accepted, null, accepted.plusSeconds(600));
    }

    @Test
    void customerCancellingAfterDriverArrivedPaysArrivedFee() {
        assertFee(20_000, TripActorRole.CUSTOMER, TripStatus.ARRIVED, accepted, arrived, arrived.plusSeconds(10));
    }

    @Test
    void noShowFeeOnlyAfterFiveMinutesWait() {
        assertFee(0, TripActorRole.DRIVER, TripStatus.ARRIVED, accepted, arrived, arrived.plusSeconds(299));
        assertFee(20_000, TripActorRole.DRIVER, TripStatus.ARRIVED, accepted, arrived, arrived.plusSeconds(300));
    }

    @Test
    void supportExceptionIsAlwaysFree() {
        assertFee(0, TripActorRole.SUPPORT_STAFF, TripStatus.ARRIVED, accepted, arrived, arrived.plusSeconds(900));
        assertFee(0, TripActorRole.SUPPORT_STAFF, TripStatus.IN_TRIP, accepted, arrived, arrived.plusSeconds(900));
    }

    private void assertFee(long expected, TripActorRole actor, TripStatus status, Instant acceptedAt, Instant arrivedAt,
                           Instant now) {
        BigDecimal fee = policy.feeFor(actor, status, acceptedAt, arrivedAt, now);
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(fee), actor + " " + status + " -> " + fee);
    }
}
