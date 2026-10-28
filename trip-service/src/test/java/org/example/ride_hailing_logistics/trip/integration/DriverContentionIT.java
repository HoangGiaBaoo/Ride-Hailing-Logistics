package org.example.ride_hailing_logistics.trip.integration;

import org.example.ride_hailing_logistics.trip.dto.response.OfferResponse;
import org.example.ride_hailing_logistics.trip.dto.response.TripResponse;
import org.example.ride_hailing_logistics.trip.enums.DriverAvailability;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-06 kich ban B: nhieu chuyen dong thoi co gan cung mot tai xe. */
class DriverContentionIT extends TripIntegrationTest {

    @Test
    void onlyOneOfManyConcurrentReservationsWins() throws Exception {
        UUID driver = onlineDriverAt(10.7772, 106.7012);
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                go.await();
                if (driverStates.reserve(driver)) wins.incrementAndGet();
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        // AVAILABLE -> OFFERED la cap nhat co dieu kien: dung mot luong doi duoc (FR-DRV-012)
        assertEquals(1, wins.get());
        assertEquals(DriverAvailability.OFFERED, availabilityOf(driver));
        // Moi lan doi trang thai phat dung mot DriverAvailabilityChanged (Online + giu = 2)
        assertEquals(2, count("select count(*) from outbox_events where event_type = 'DriverAvailabilityChanged' and aggregate_id = ?",
                driver.toString()));
    }

    @Test
    void manyTripsCompetingForOneDriverAssignHimToExactlyOne() throws Exception {
        UUID driver = onlineDriverAt(10.7772, 106.7012);
        List<UUID> customers = new ArrayList<>();
        for (int i = 0; i < 8; i++) customers.add(newCustomer());

        ExecutorService pool = Executors.newFixedThreadPool(customers.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<TripResponse>> futures = new ArrayList<>();
        for (UUID c : customers) {
            futures.add(pool.submit(() -> {
                go.await();
                return requestRide(c);
            }));
        }
        go.countDown();
        for (var f : futures) assertNotNull(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        OfferResponse offer = await(() -> dispatchService.currentOfferFor(driver).orElse(null), 10_000);
        assertNotNull(offer);
        // Tai mot thoi diem tai xe chi giu toi da MOT offer (unique uq_offer_pending_driver)
        assertEquals(1, count("select count(*) from driver_offers where driver_id = ? and status = 'PENDING'", driver));
        dispatchService.accept(offer.offerId(), driver);

        Thread.sleep(1500);   // cho cac vong dispatch khac chay them
        assertEquals(1, count("select count(*) from trips where driver_id = ?", driver));
        assertEquals(0, count("select count(*) from driver_offers where driver_id = ? and status = 'PENDING'", driver));
        assertEquals(DriverAvailability.BUSY, availabilityOf(driver));
        // Cac chuyen con lai van cho tai xe khac, khong chuyen nao "an trom" tai xe dang BUSY
        assertEquals(customers.size() - 1, count("select count(*) from trips where status = 'MATCHING' and driver_id is null"));
    }
}
