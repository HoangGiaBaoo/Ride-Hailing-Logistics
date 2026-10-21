package org.example.ride_hailing_logistics.driver;

import org.example.ride_hailing_logistics.driver.DriverEnums.DriverAvailability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-06 kich ban B o phia driver-service: giu tai xe la cap nhat co dieu kien tren PostgreSQL, theo offer/chuyen
 * dang giu. Lenh tu trip-service den qua mang co the trung lap hoac lech thu tu — moi truong hop deu phai dung.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class DriverHoldIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private DriverAvailabilityService availability;
    @Autowired private DriverProfileRepository profiles;

    private UUID driver;

    @BeforeEach
    void approvedAvailableDriver() {
        jdbc.execute("TRUNCATE driver_review_decisions, driver_documents, vehicles, driver_profiles, outbox_events, inbox_events, processed_events CASCADE");
        // Ho so da duyet chen thang: luong dang ky/duyet can identity-service, khong phai doi tuong cua test nay
        driver = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO driver_profiles (id, review_status, availability, availability_changed_at, created_at, updated_at)
                VALUES (?, 'APPROVED', 'AVAILABLE', now(), now(), now())
                """, driver);
        jdbc.update("""
                INSERT INTO vehicles (id, driver_id, vehicle_type, plate_number, brand, model, color, manufacture_year, status, created_at, updated_at)
                VALUES (?, ?, 'MOTORBIKE', '59X99999', 'Honda', 'Wave', 'Den', 2022, 'ACTIVE', now(), now())
                """, UUID.randomUUID(), driver);
    }

    @Test
    void onlyOneOfFiftyConcurrentReservationsWins() throws Exception {
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        Set<UUID> winners = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < threads; i++) {
            UUID offerId = UUID.randomUUID();   // 50 chuyen khac nhau cung muon giu mot tai xe
            pool.submit(() -> {
                go.await();
                if (availability.reserve(driver, offerId)) winners.add(offerId);
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        // AVAILABLE -> OFFERED la cap nhat co dieu kien: dung mot offer giu duoc tai xe (FR-DRV-012)
        assertEquals(1, winners.size());
        HoldState hold = profiles.currentHold(driver).orElseThrow();
        assertEquals(DriverAvailability.OFFERED, hold.availability());
        assertEquals(winners.iterator().next(), hold.ref());
    }

    @Test
    void retriedCommandsAreIdempotentAndStaleCommandsCannotTouchANewerHold() {
        UUID offerA = UUID.randomUUID();
        UUID offerB = UUID.randomUUID();
        UUID trip = UUID.randomUUID();

        assertTrue(availability.reserve(driver, offerA));
        assertTrue(availability.reserve(driver, offerA), "retry after timeout returns the same result");
        assertFalse(availability.reserve(driver, offerB), "another offer cannot take a held driver");

        assertFalse(availability.markBusy(driver, offerB, trip), "accept through an offer that does not hold the driver");
        assertTrue(availability.markBusy(driver, offerA, trip));
        assertTrue(availability.markBusy(driver, offerA, trip), "retried accept is idempotent");

        // Lenh tra offer A den tre sau khi tai xe da BUSY cho chuyen: khong duoc tha tai xe ra
        assertFalse(availability.releaseOffer(driver, offerA));
        assertEquals(DriverAvailability.BUSY, profiles.currentHold(driver).orElseThrow().availability());

        assertFalse(availability.finishTrip(driver, UUID.randomUUID()), "another trip cannot release the driver");
        assertTrue(availability.finishTrip(driver, trip));
        assertFalse(availability.finishTrip(driver, trip), "second release is a no-op");
        assertEquals(new HoldState(DriverAvailability.AVAILABLE, null), profiles.currentHold(driver).orElseThrow());
    }

    @Test
    void rollbackCompensationRestoresTheOffer() {
        UUID offer = UUID.randomUUID();
        UUID trip = UUID.randomUUID();
        availability.reserve(driver, offer);
        availability.markBusy(driver, offer, trip);
        // trip-service rollback sau markBusy -> tai xe quay lai OFFERED cho dung offer do, khong ve AVAILABLE
        assertTrue(availability.revertBusy(driver, trip, offer));
        assertEquals(new HoldState(DriverAvailability.OFFERED, offer), profiles.currentHold(driver).orElseThrow());
        assertTrue(availability.releaseOffer(driver, offer));
        assertEquals(DriverAvailability.AVAILABLE, profiles.currentHold(driver).orElseThrow().availability());
    }
}
