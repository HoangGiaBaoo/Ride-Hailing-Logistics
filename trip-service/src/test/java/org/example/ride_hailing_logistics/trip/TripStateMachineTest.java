package org.example.ride_hailing_logistics.trip;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Doi chieu 1:1 voi ma tran chuyen trang thai SRS §7.1 (FR-TRIP-008, FR-TRIP-009). */
class TripStateMachineTest {

    @ParameterizedTest(name = "{0} -> {1} by {2} is allowed")
    @CsvSource({
            "CREATED, MATCHING, SYSTEM",
            "CREATED, CANCELLED, CUSTOMER",
            "MATCHING, ACCEPTED, DRIVER",
            "MATCHING, NO_DRIVER, SYSTEM",
            "MATCHING, CANCELLED, CUSTOMER",
            "MATCHING, CANCELLED, SUPPORT_STAFF",
            "ACCEPTED, PICKING_UP, DRIVER",
            "ACCEPTED, CANCELLED, CUSTOMER",
            "ACCEPTED, CANCELLED, DRIVER",
            "PICKING_UP, ARRIVED, DRIVER",
            "PICKING_UP, CANCELLED, DRIVER",
            "ARRIVED, IN_TRIP, DRIVER",
            "ARRIVED, CANCELLED, CUSTOMER",
            "IN_TRIP, COMPLETED, DRIVER",
            "IN_TRIP, CANCELLED, SUPPORT_STAFF"
    })
    void transitionsFromSrsMatrixAreAllowed(TripStatus from, TripStatus to, TripActorRole actor) {
        assertTrue(TripStateMachine.isAllowed(from, to, actor));
        assertDoesNotThrow(() -> TripStateMachine.assertAllowed(from, to, actor));
    }

    @ParameterizedTest(name = "{0} -> {1} is not a defined transition")
    @CsvSource({
            "ACCEPTED, IN_TRIP",       // nhay coc, bo qua don khach
            "ACCEPTED, COMPLETED",
            "PICKING_UP, IN_TRIP",
            "ARRIVED, PICKING_UP",     // di lui
            "IN_TRIP, ARRIVED",
            "MATCHING, PICKING_UP",
            "CREATED, ACCEPTED",
            "NO_DRIVER, MATCHING"
    })
    void skippingOrGoingBackwardsIsRejected(TripStatus from, TripStatus to) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> TripStateMachine.assertAllowed(from, to, TripActorRole.DRIVER));
        assertEquals(ErrorCode.INVALID_TRIP_STATE, ex.getCode());
    }

    @ParameterizedTest
    @EnumSource(value = TripStatus.class, names = {"COMPLETED", "CANCELLED", "NO_DRIVER"})
    void terminalStatesHaveNoOutgoingTransition(TripStatus terminal) {
        for (TripStatus to : TripStatus.values()) {
            for (TripActorRole actor : TripActorRole.values()) {
                assertFalse(TripStateMachine.isAllowed(terminal, to, actor), terminal + " -> " + to + " by " + actor);
            }
        }
    }

    @Test
    void onlySupportStaffMayCancelATripInProgress() {
        // SRS §7.1: IN_TRIP -> CANCELLED "chi qua quy trinh ngoai le duoc phe duyet"
        assertFalse(TripStateMachine.isAllowed(TripStatus.IN_TRIP, TripStatus.CANCELLED, TripActorRole.CUSTOMER));
        assertFalse(TripStateMachine.isAllowed(TripStatus.IN_TRIP, TripStatus.CANCELLED, TripActorRole.DRIVER));
        assertTrue(TripStateMachine.isAllowed(TripStatus.IN_TRIP, TripStatus.CANCELLED, TripActorRole.SUPPORT_STAFF));
    }

    @Test
    void customerCannotPerformDriverSteps() {
        assertFalse(TripStateMachine.isAllowed(TripStatus.MATCHING, TripStatus.ACCEPTED, TripActorRole.CUSTOMER));
        assertFalse(TripStateMachine.isAllowed(TripStatus.IN_TRIP, TripStatus.COMPLETED, TripActorRole.CUSTOMER));
        assertFalse(TripStateMachine.isAllowed(TripStatus.MATCHING, TripStatus.NO_DRIVER, TripActorRole.DRIVER));
    }

    @Test
    void unknownActorIsAlwaysRejected() {
        assertFalse(TripStateMachine.isAllowed(TripStatus.MATCHING, TripStatus.CANCELLED, null));
    }
}
