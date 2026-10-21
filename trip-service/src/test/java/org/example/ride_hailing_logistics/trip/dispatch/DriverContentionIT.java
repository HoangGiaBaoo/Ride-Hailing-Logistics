package org.example.ride_hailing_logistics.trip.dispatch;

import org.example.ride_hailing_logistics.InMemoryDriverPool.State;
import org.example.ride_hailing_logistics.IntegrationTestSupport;
import org.example.ride_hailing_logistics.trip.TripDtos.TripResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * UC-06 kich ban B: nhieu chuyen dong thoi co gan cung mot tai xe.
 * (50 luong giu cung mot tai xe tren DB that: DriverHoldIT cua driver-service.)
 */
class DriverContentionIT extends IntegrationTestSupport {

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

        DispatchService.OfferView offer = await(() -> dispatchService.currentOfferFor(driver).orElse(null), 10_000);
        assertNotNull(offer);
        // Tai mot thoi diem tai xe chi giu toi da MOT offer (giu nguyen tu o driver-service + uq_offer_pending_driver)
        assertEquals(1, count("select count(*) from driver_offers where driver_id = ? and status = 'PENDING'", driver));
        dispatchService.accept(offer.offerId(), driver);

        Thread.sleep(1500);   // cho cac vong dispatch khac chay them
        assertEquals(1, count("select count(*) from trips where driver_id = ?", driver));
        assertEquals(0, count("select count(*) from driver_offers where driver_id = ? and status = 'PENDING'", driver));
        assertEquals(State.BUSY, drivers.stateOf(driver));
        // Cac chuyen con lai van cho tai xe khac, khong chuyen nao "an trom" tai xe dang BUSY
        assertEquals(customers.size() - 1, count("select count(*) from trips where status = 'MATCHING' and driver_id is null"));
    }
}
