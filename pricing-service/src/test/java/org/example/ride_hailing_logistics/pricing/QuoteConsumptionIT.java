package org.example.ride_hailing_logistics.pricing;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.Place;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.PricingRuleRequest;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
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

/**
 * Quote dung mot lan qua API noi bo ma trip-service goi (FR-PRI-014, FR-PRI-015, BR-005):
 * goi lai cung chuyen -> cung ket qua; chuyen khac -> tu choi; bu tru chi tra duoc boi dung chuyen da dung.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class QuoteConsumptionIT {

    private static final Place PICKUP = new Place(10.7769, 106.7009, "Nhà thờ Đức Bà");
    private static final Place DROPOFF = new Place(10.7626, 106.6602, "ĐH Bách Khoa");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private PricingRuleService pricingRules;
    @Autowired private QuoteService quoteService;

    private final UUID customer = UUID.randomUUID();

    @BeforeEach
    void rule() {
        jdbc.execute("TRUNCATE fare_quotes, pricing_rules, audit_records, outbox_events, inbox_events, processed_events CASCADE");
        pricingRules.create(new PricingRuleRequest(ServiceType.RIDE, VehicleType.MOTORBIKE, "HCM", bd(10_000), bd(4_500),
                bd(300), bd(15_000), bd(2_000), Instant.now(), null, "test"), new Actor(UUID.randomUUID(), Set.of(Role.ADMIN)));
    }

    @Test
    void consumeIsRetrySafeForTheSameTripOnly() {
        UUID quoteId = newQuote();
        UUID tripA = UUID.randomUUID();
        UUID tripB = UUID.randomUUID();

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, assertThrows(BusinessException.class,
                () -> quoteService.consumeQuote(quoteId, UUID.randomUUID(), tripA)).getCode(), "quote of another customer");
        assertEquals(quoteId, quoteService.consumeQuote(quoteId, customer, tripA).quoteId());
        assertEquals(quoteId, quoteService.consumeQuote(quoteId, customer, tripA).quoteId(), "retry after timeout");
        assertEquals(ErrorCode.QUOTE_EXPIRED, assertThrows(BusinessException.class,
                () -> quoteService.consumeQuote(quoteId, customer, tripB)).getCode(), "second trip from one quote");

        quoteService.releaseQuote(quoteId, tripB);   // chuyen khong dung quote thi khong tra duoc
        assertEquals(ErrorCode.QUOTE_EXPIRED, assertThrows(BusinessException.class,
                () -> quoteService.consumeQuote(quoteId, customer, tripB)).getCode());
        quoteService.releaseQuote(quoteId, tripA);   // trip-service rollback viec tao chuyen A
        assertEquals(quoteId, quoteService.consumeQuote(quoteId, customer, tripB).quoteId());
    }

    @Test
    void twentyConcurrentTripsCanUseAQuoteOnlyOnce() throws Exception {
        UUID quoteId = newQuote();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        Set<UUID> winners = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < threads; i++) {
            UUID trip = UUID.randomUUID();
            pool.submit(() -> {
                go.await();
                try {
                    quoteService.consumeQuote(quoteId, customer, trip);
                    winners.add(trip);
                } catch (BusinessException ignored) {
                    // cac chuyen thua nhan QUOTE_EXPIRED
                }
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(1, winners.size());
        assertEquals(winners.iterator().next(),
                jdbc.queryForObject("select used_by_trip from fare_quotes where id = ?", UUID.class, quoteId));
    }

    private UUID newQuote() {
        return quoteService.createQuote(customer, new QuoteRequest(ServiceType.RIDE, VehicleType.MOTORBIKE, PICKUP, DROPOFF)).quoteId();
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }
}
