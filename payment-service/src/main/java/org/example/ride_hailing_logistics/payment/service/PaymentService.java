package org.example.ride_hailing_logistics.payment.service;

import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.payment.dto.response.PaymentResponse;
import org.example.ride_hailing_logistics.payment.entity.Payment;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Thanh toan va hoan tien (FR-PAY-001...012). */
public interface PaymentService {

    /** Idempotent theo (trip, loai) (FR-PAY-003). */
    void createForTrip(UUID tripId, String tripCode, Payment.Kind kind, UUID customerId, UUID driverId,
                       ServiceType serviceType, BigDecimal amount, String currency, PaymentMethod method);

    void submitToProvider(UUID paymentId);

    /** Goi trong transaction da khoa payment. */
    void markSucceeded(Payment payment, Instant now);

    /** Goi trong transaction da khoa payment. */
    void markFailed(Payment payment, String reason, Instant now);

    PaymentResponse retry(UUID paymentId, UUID customerId);

    PaymentResponse refund(UUID paymentId, BigDecimal amount, String reason, Actor actor);

    List<PaymentResponse> forTrip(UUID tripId, Actor actor);

    PageResponse<PaymentResponse> list(Payment.Status status, Pageable pageable);
}
