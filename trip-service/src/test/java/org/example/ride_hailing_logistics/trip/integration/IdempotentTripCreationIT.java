package org.example.ride_hailing_logistics.trip.integration;

import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.platform.idempotency.IdempotencyService;
import org.example.ride_hailing_logistics.trip.dto.request.CreateTripRequest;
import org.example.ride_hailing_logistics.trip.dto.response.TripResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-TRIP-002, BR-015: gui lap cung Idempotency-Key khong tao chuyen moi, ke ca khi lan dau chet giua chung. */
class IdempotentTripCreationIT extends TripIntegrationTest {

    @Autowired
    private IdempotencyService idempotency;

    @Test
    void concurrentRetriesWithSameKeyCreateOneTrip() throws Exception {
        UUID customer = newCustomer();
        CreateTripRequest req = rideRequest(pricing.issue(customer));
        String key = "same-key-" + UUID.randomUUID();

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        Set<UUID> returnedTripIds = ConcurrentHashMap.newKeySet();
        Set<ErrorCode> errors = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                go.await();
                try {
                    returnedTripIds.add(idempotency.execute(customer, "trip.create", key, req, TripResponse.class,
                            () -> tripService.create(customer, key, req)).id());
                } catch (BusinessException e) {
                    errors.add(e.getCode());   // request den luc lan dau dang chay: CONFLICT "dang xu ly"
                }
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, returnedTripIds.size(), "all successful retries must see the same trip");
        assertTrue(errors.stream().allMatch(c -> c == ErrorCode.CONFLICT), "errors: " + errors);
        assertEquals(1, count("select count(*) from trips where customer_id = ?", customer));

        // Lan gui lai sau do (tuan tu) tra dung chuyen cu
        UUID replay = idempotency.execute(customer, "trip.create", key, req, TripResponse.class,
                () -> tripService.create(customer, key, req)).id();
        assertEquals(returnedTripIds.iterator().next(), replay);
    }

    /**
     * Ban ghi idempotency bi mat (vd tien trinh chet sau khi tieu quote, truoc khi luu ket qua): goi lai cung khoa
     * van ra cung tripId -> pricing nhan ra va tra lai cung snapshot, khong tao chuyen thu hai.
     */
    @Test
    void retryWithoutIdempotencyRecordStillYieldsSameTrip() {
        UUID customer = newCustomer();
        CreateTripRequest req = rideRequest(pricing.issue(customer));
        UUID first = tripService.create(customer, "crash-key-1", req).id();
        UUID second = tripService.create(customer, "crash-key-1", req).id();
        assertEquals(first, second);
        assertEquals(1, count("select count(*) from trips where customer_id = ?", customer));
        assertEquals(1, pricing.consumedCount());
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        UUID customer = newCustomer();
        CreateTripRequest req = rideRequest(pricing.issue(customer));
        idempotency.execute(customer, "trip.create", "key-12345678", req, TripResponse.class,
                () -> tripService.create(customer, "key-12345678", req));
        CreateTripRequest changed = new CreateTripRequest(req.quoteId(), PaymentMethod.SANDBOX_CARD, true, null, null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> idempotency.execute(customer, "trip.create",
                "key-12345678", changed, TripResponse.class, () -> tripService.create(customer, "key-12345678", changed)));
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED, ex.getCode());
    }

    @Test
    void quoteCannotBeReusedForSecondTripOrByAnotherCustomer() {
        UUID customer = newCustomer();
        UUID other = newCustomer();
        CreateTripRequest req = rideRequest(pricing.issue(customer));
        // Quote cua nguoi khac: 404, khong lo su ton tai (FR-PRI-014)
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,
                assertThrows(BusinessException.class, () -> tripService.create(other, "k-other", req)).getCode());
        tripService.create(customer, "k-1", req);
        // Mot khach mot chuyen hoat dong (FR-TRIP-007) duoc kiem truoc ca viec dung lai quote
        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION,
                assertThrows(BusinessException.class, () -> tripService.create(customer, "k-2", req)).getCode());
    }
}
