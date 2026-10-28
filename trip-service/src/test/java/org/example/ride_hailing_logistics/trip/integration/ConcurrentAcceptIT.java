package org.example.ride_hailing_logistics.trip.integration;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.trip.dto.response.OfferResponse;
import org.example.ride_hailing_logistics.trip.dto.response.TripResponse;
import org.example.ride_hailing_logistics.trip.entity.Trip;
import org.example.ride_hailing_logistics.trip.enums.DriverAvailability;
import org.example.ride_hailing_logistics.trip.enums.TripStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** UC-06 kich ban A / NFR-PERF-007: 100 yeu cau dong thoi chap nhan cung mot offer. */
class ConcurrentAcceptIT extends TripIntegrationTest {

    @Test
    void hundredConcurrentAcceptsProduceExactlyOneAssignment() throws Exception {
        UUID customer = newCustomer();
        UUID driver = onlineDriverAt(10.7772, 106.7012);
        TripResponse trip = requestRide(customer);
        OfferResponse offer = await(() -> dispatchService.currentOfferFor(driver).orElse(null), 10_000);
        assertNotNull(offer, "dispatch should offer the only nearby driver");

        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ConcurrentLinkedQueue<UUID> assignedDrivers = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();   // tha 100 luong cung luc, khong de chung chay tuan tu
                    Trip result = dispatchService.accept(offer.offerId(), driver);
                    assignedDrivers.add(result.getDriverId());
                } catch (BusinessException e) {
                    if (e.getCode() != ErrorCode.CONFLICT && e.getCode() != ErrorCode.DRIVER_ALREADY_ASSIGNED) unexpected.add(e);
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        assertTrue(unexpected.isEmpty(), "unexpected failures: " + unexpected);
        // Gui lap request da thang tra cung ket qua logic (FR-MAT-012): moi phan hoi deu cung mot tai xe
        assertTrue(assignedDrivers.stream().allMatch(driver::equals));
        // Bat bien trong DB: dung mot offer ACCEPTED, dung mot buoc ACCEPTED trong lich su, mot event TripAccepted
        assertEquals(1, count("select count(*) from driver_offers where trip_id = ? and status = 'ACCEPTED'", trip.id()));
        assertEquals(1, count("select count(*) from trip_status_history where trip_id = ? and to_status = 'ACCEPTED'", trip.id()));
        assertEquals(1, count("select count(*) from outbox_events where aggregate_id = ? and event_type = 'TripAccepted'", trip.id().toString()));
        assertEquals(TripStatus.ACCEPTED.name(), jdbc.queryForObject("select status from trips where id = ?", String.class, trip.id()));
        assertEquals(DriverAvailability.BUSY, availabilityOf(driver));
    }

    @Test
    void anotherDriverCannotAcceptSomeoneElsesOffer() throws Exception {
        UUID customer = newCustomer();
        UUID driver = onlineDriverAt(10.7772, 106.7012);
        UUID intruder = onlineDriverAt(10.80, 106.75);   // xa hon, khong duoc chon
        requestRide(customer);
        OfferResponse offer = await(() -> dispatchService.currentOfferFor(driver).orElse(null), 10_000);
        assertNotNull(offer);
        BusinessException ex = assertThrows(BusinessException.class, () -> dispatchService.accept(offer.offerId(), intruder));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getCode());
        assertEquals(DriverAvailability.AVAILABLE, availabilityOf(intruder));
    }
}
