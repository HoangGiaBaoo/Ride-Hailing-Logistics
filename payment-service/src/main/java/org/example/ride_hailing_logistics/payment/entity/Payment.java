package org.example.ride_hailing_logistics.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Thanh toan cua mot chuyen (FR-PAY-001). So tien luon lay tu cuoc da chot, khong tu client (FR-PAY-002).
 * Khong luu du lieu the; chi providerRef (FR-PAY-006, CON-08).
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    public enum Kind { TRIP_FARE, CANCELLATION_FEE }

    /** FR-PAY-007. */
    public enum Status { PENDING, SUCCEEDED, FAILED, REFUND_PENDING, PARTIALLY_REFUNDED, REFUNDED }

    @Id
    private UUID id;

    @Column(name = "trip_id", nullable = false)
    private UUID tripId;

    @Column(name = "trip_code", nullable = false, length = 20)
    private String tripCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Kind kind;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private ServiceType serviceType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal refundedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_ref", length = 64)
    private String providerRef;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "succeeded_at")
    private Instant succeededAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    // Wrapper (khong phai long): version null = entity moi -> Spring Data goi persist() chu khong merge()
    private Long version;

    public static Payment create(UUID tripId, String tripCode, Kind kind, UUID customerId, UUID driverId, ServiceType serviceType,
                          BigDecimal amount, String currency, PaymentMethod method, Instant now) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.tripId = tripId;
        p.tripCode = tripCode;
        p.kind = kind;
        p.customerId = customerId;
        p.driverId = driverId;
        p.serviceType = serviceType;
        p.amount = amount;
        p.refundedAmount = BigDecimal.ZERO;
        p.currency = currency;
        p.method = method;
        p.status = Status.PENDING;
        p.provider = method == PaymentMethod.CASH ? "CASH" : "SANDBOX";
        p.attempts = method == PaymentMethod.CASH ? 0 : 1;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public void attachProviderRef(String ref, Instant now) {
        providerRef = ref;
        updatedAt = now;
    }

    /** Chi PENDING moi duoc doi; callback lap/sai thu tu den sau khong lam gi (FR-PAY-005). */
    public boolean succeed(Instant now) {
        if (status != Status.PENDING) return false;
        status = Status.SUCCEEDED;
        succeededAt = now;
        failureReason = null;
        updatedAt = now;
        return true;
    }

    public boolean fail(String reason, Instant now) {
        if (status != Status.PENDING) return false;
        status = Status.FAILED;
        failureReason = reason;
        updatedAt = now;
        return true;
    }

    /** FR-PAY-008: thanh toan loi -> khoan phai thu, cho thu lai co gioi han, khong hoan tac chuyen. */
    public void retry(int maxAttempts, Instant now) {
        if (status != Status.FAILED) {
            throw BusinessException.rule("Chỉ thử lại được thanh toán đang thất bại.");
        }
        if (attempts >= maxAttempts) {
            throw BusinessException.rule("Đã hết số lần thử lại thanh toán, vui lòng liên hệ hỗ trợ.");
        }
        attempts++;
        status = Status.PENDING;
        providerRef = null;
        failureReason = null;
        updatedAt = now;
    }

    public BigDecimal refundable() {
        return amount.subtract(refundedAmount);
    }

    public void applyRefund(BigDecimal refund, Instant now) {
        // BR-010: tong hoan luy ke khong vuot so da thu (DB con CHECK refunded_amount <= amount)
        if (refund.compareTo(refundable()) > 0) {
            throw BusinessException.rule("Số tiền hoàn vượt quá số đã thu còn lại (" + refundable().toPlainString() + ").");
        }
        refundedAmount = refundedAmount.add(refund);
        status = refundedAmount.compareTo(amount) == 0 ? Status.REFUNDED : Status.PARTIALLY_REFUNDED;
        updatedAt = now;
    }

    public boolean isRefundable() {
        return status == Status.SUCCEEDED || status == Status.PARTIALLY_REFUNDED;
    }
}
