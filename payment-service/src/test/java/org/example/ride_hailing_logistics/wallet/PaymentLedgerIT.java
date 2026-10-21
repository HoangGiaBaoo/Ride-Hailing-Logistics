package org.example.ride_hailing_logistics.wallet;

import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.payment.Payment;
import org.example.ride_hailing_logistics.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Thanh toan tien mat -> PaymentSucceeded di qua outbox -> Redis Stream -> inbox cua chinh payment-service -> ghi vi.
 * Moi buoc tai chinh chi xay ra mot lan du event/lenh bi chay lai (BR-009, BR-015, FR-WAL-005, FR-PAY-003).
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class PaymentLedgerIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private PaymentService paymentService;
    @Autowired private WalletService walletService;

    @BeforeEach
    void reset() {
        jdbc.execute("""
                TRUNCATE outbox_events, inbox_events, processed_events, audit_records, idempotency_records, refunds,
                payments, payment_callback_events, wallet_entries, wallets, commission_rules CASCADE
                """);
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        // API khong cho hieu luc o qua khu nen chen thang mot quy tac dang co hieu luc
        jdbc.update("""
                INSERT INTO commission_rules (id, service_type, rate, rule_version, effective_from, created_at)
                VALUES (?, 'RIDE', 0.20, 1, now() - interval '1 minute', now())
                """, UUID.randomUUID());
    }

    @Test
    void cashTripPostsCommissionExactlyOnce() throws Exception {
        UUID trip = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID driver = UUID.randomUUID();
        BigDecimal fare = new BigDecimal("87000");
        paymentService.createForTrip(trip, "TTEST01", Payment.Kind.TRIP_FARE, customer, driver, ServiceType.RIDE,
                fare, "VND", PaymentMethod.CASH);

        // Di vong qua Redis Stream that va quay ve inbox cua payment-service
        Integer entries = await(() -> {
            Integer n = jdbc.queryForObject("select count(*) from wallet_entries", Integer.class);
            return n != null && n > 0 ? n : null;
        }, 15_000);
        assertNotNull(entries, "wallet was never credited");
        // 20% cua 87.000 = 17.400; tien mat -> tai xe da cam tien, vi chi ghi no hoa hong
        BigDecimal balance = walletService.walletOf(driver).balance();
        assertEquals(0, new BigDecimal("-17400").compareTo(balance), "balance " + balance);

        // Chay lai: tao thanh toan lan hai va ghi vi lan hai cho cung payment -> khong co gi moi
        UUID paymentId = jdbc.queryForObject("select id from payments where trip_id = ?", UUID.class, trip);
        paymentService.createForTrip(trip, "TTEST01", Payment.Kind.TRIP_FARE, customer, driver, ServiceType.RIDE,
                fare, "VND", PaymentMethod.CASH);
        walletService.postPaymentEarnings(new WalletService.PaymentSucceededView(paymentId, trip, "TTEST01",
                "TRIP_FARE", driver, ServiceType.RIDE, fare, "VND", "CASH", Instant.now()));
        assertEquals(1, jdbc.queryForObject("select count(*) from payments where trip_id = ?", Integer.class, trip));
        assertEquals(entries, jdbc.queryForObject("select count(*) from wallet_entries", Integer.class));
        assertEquals(0, balance.compareTo(walletService.walletOf(driver).balance()));
        assertEquals(1, jdbc.queryForObject("select count(*) from processed_events where handler = 'wallet.post-earnings'", Integer.class));
    }

    private static <T> T await(java.util.function.Supplier<T> probe, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            T value = probe.get();
            if (value != null) return value;
            Thread.sleep(100);
        }
        return null;
    }
}
