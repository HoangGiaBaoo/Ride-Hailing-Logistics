package org.example.ride_hailing_logistics.payment.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.function.Supplier;

/** Nen integration test cua payment-service: PostgreSQL rhl_payment_test, Redis DB 1, Kafka namespace rhltest. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
abstract class PaymentIntegrationTest {

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected StringRedisTemplate redis;

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

    protected static <T> T await(Supplier<T> probe, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            T value = probe.get();
            if (value != null) return value;
            Thread.sleep(100);
        }
        return null;
    }
}
