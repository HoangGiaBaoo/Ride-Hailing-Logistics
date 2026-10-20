package org.example.ride_hailing_logistics.trip;

import org.example.ride_hailing_logistics.IntegrationTestSupport;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.platform.idempotency.IdempotencyService;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteRequest;
import org.example.ride_hailing_logistics.trip.TripDtos.CreateTripRequest;
import org.example.ride_hailing_logistics.trip.TripDtos.TripResponse;
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

/** FR-TRIP-002, BR-015: gui lap cung Idempotency-Key khong tao chuyen moi. */
class IdempotentTripCreationIT extends IntegrationTestSupport {

    @Autowired
    private IdempotencyService idempotency;

    @Test
    void concurrentRetriesWithSameKeyCreateOneTrip() throws Exception {
        UUID customer = newCustomer();
        var quote = quoteService.createQuote(customer, new QuoteRequest(ServiceType.RIDE, VehicleType.MOTORBIKE, PICKUP, DROPOFF));
        CreateTripRequest req = new CreateTripRequest(quote.quoteId(), PaymentMethod.CASH, true, null, null, null);
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
                            () -> tripService.create(customer, req)).id());
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
        assertEquals(1, jdbc.queryForObject("select count(*) from trips where customer_id = ?", Integer.class, customer));

        // Lan gui lai sau do (tuan tu) tra dung chuyen cu
        UUID replay = idempotency.execute(customer, "trip.create", key, req, TripResponse.class,
                () -> tripService.create(customer, req)).id();
        assertEquals(returnedTripIds.iterator().next(), replay);
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        UUID customer = newCustomer();
        var quote = quoteService.createQuote(customer, new QuoteRequest(ServiceType.RIDE, VehicleType.MOTORBIKE, PICKUP, DROPOFF));
        CreateTripRequest req = new CreateTripRequest(quote.quoteId(), PaymentMethod.CASH, true, null, null, null);
        idempotency.execute(customer, "trip.create", "key-12345678", req, TripResponse.class, () -> tripService.create(customer, req));
        CreateTripRequest changed = new CreateTripRequest(quote.quoteId(), PaymentMethod.SANDBOX_CARD, true, null, null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> idempotency.execute(customer, "trip.create",
                "key-12345678", changed, TripResponse.class, () -> tripService.create(customer, changed)));
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_REUSED, ex.getCode());
    }

    @Test
    void quoteCannotBeReusedForSecondTripOrByAnotherCustomer() {
        UUID customer = newCustomer();
        UUID other = newCustomer();
        var quote = quoteService.createQuote(customer, new QuoteRequest(ServiceType.RIDE, VehicleType.MOTORBIKE, PICKUP, DROPOFF));
        CreateTripRequest req = new CreateTripRequest(quote.quoteId(), PaymentMethod.CASH, true, null, null, null);
        // Quote cua nguoi khac: 404, khong lo su ton tai (FR-PRI-014)
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,
                assertThrows(BusinessException.class, () -> tripService.create(other, req)).getCode());
        tripService.create(customer, req);
        // Mot khach mot chuyen hoat dong (FR-TRIP-007) duoc kiem truoc ca viec dung lai quote
        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION,
                assertThrows(BusinessException.class, () -> tripService.create(customer, req)).getCode());
    }
}
