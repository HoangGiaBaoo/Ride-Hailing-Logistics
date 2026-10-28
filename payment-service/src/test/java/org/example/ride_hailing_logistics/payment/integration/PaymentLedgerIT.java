package org.example.ride_hailing_logistics.payment.integration;

import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.payment.dto.PaymentSucceededView;
import org.example.ride_hailing_logistics.payment.entity.Payment;
import org.example.ride_hailing_logistics.payment.service.PaymentService;
import org.example.ride_hailing_logistics.payment.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Thanh toan tien mat -> PaymentSucceeded qua outbox -> Kafka that -> inbox cua chinh payment-service -> ghi vi.
 * Moi buoc tai chinh chi xay ra mot lan du event/lenh bi chay lai (BR-009, BR-015, FR-WAL-005, FR-PAY-003).
 */
class PaymentLedgerIT extends PaymentIntegrationTest {

    @Autowired private PaymentService paymentService;
    @Autowired private WalletService walletService;

    @Test
    void cashTripPostsCommissionExactlyOnce() throws Exception {
        UUID trip = UUID.randomUUID();
        UUID customer = UUID.randomUUID();
        UUID driver = UUID.randomUUID();
        BigDecimal fare = new BigDecimal("87000");
        paymentService.createForTrip(trip, "TTEST01", Payment.Kind.TRIP_FARE, customer, driver, ServiceType.RIDE,
                fare, "VND", PaymentMethod.CASH);

        // Di vong qua Kafka that va quay ve inbox cua payment-service
        Integer entries = await(() -> {
            Integer n = jdbc.queryForObject("select count(*) from wallet_entries", Integer.class);
            return n != null && n > 0 ? n : null;
        }, 30_000);
        assertNotNull(entries, "wallet was never credited");
        // 20% cua 87.000 = 17.400; tien mat -> tai xe da cam tien, vi chi ghi no hoa hong
        BigDecimal balance = walletService.walletOf(driver).balance();
        assertEquals(0, new BigDecimal("-17400").compareTo(balance), "balance " + balance);

        // Chay lai: tao thanh toan lan hai va ghi vi lan hai cho cung payment -> khong co gi moi
        UUID paymentId = jdbc.queryForObject("select id from payments where trip_id = ?", UUID.class, trip);
        paymentService.createForTrip(trip, "TTEST01", Payment.Kind.TRIP_FARE, customer, driver, ServiceType.RIDE,
                fare, "VND", PaymentMethod.CASH);
        walletService.postPaymentEarnings(new PaymentSucceededView(paymentId, trip, "TTEST01",
                "TRIP_FARE", driver, ServiceType.RIDE, fare, "VND", "CASH", Instant.now()));
        assertEquals(1, jdbc.queryForObject("select count(*) from payments where trip_id = ?", Integer.class, trip));
        assertEquals(entries, jdbc.queryForObject("select count(*) from wallet_entries", Integer.class));
        assertEquals(0, balance.compareTo(walletService.walletOf(driver).balance()));
    }
}
