package org.example.ride_hailing_logistics.payment;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    /** Khong co truong so tien hoan "tuy y" ngoai so duong; tong luy ke duoc kiem o server (BR-010). */
    public record RefundRequest(@NotNull @Positive @Digits(integer = 13, fraction = 0) BigDecimal amount,
                                @NotBlank @Size(max = 500) String reason) {}

    public record RefundView(UUID id, BigDecimal amount, String currency, String reason, String status, Instant createdAt) {
        static RefundView from(Refund r) {
            return new RefundView(r.getId(), r.getAmount(), r.getCurrency(), r.getReason(), r.getStatus(), r.getCreatedAt());
        }
    }

    /** Bien nhan cho khach (UI-CUS-007): khong lo providerRef day du. */
    public record PaymentResponse(UUID id, UUID tripId, Payment.Kind kind, BigDecimal amount, BigDecimal refundedAmount,
                                  String currency, PaymentMethod method, Payment.Status status, int attempts,
                                  String failureReason, Instant succeededAt, Instant createdAt, List<RefundView> refunds) {
        static PaymentResponse from(Payment p, List<Refund> refunds) {
            return new PaymentResponse(p.getId(), p.getTripId(), p.getKind(), p.getAmount(), p.getRefundedAmount(),
                    p.getCurrency(), p.getMethod(), p.getStatus(), p.getAttempts(), p.getFailureReason(),
                    p.getSucceededAt(), p.getCreatedAt(), refunds.stream().map(RefundView::from).toList());
        }
    }

    public record CallbackPayload(String eventId, String providerRef, String paymentReference, String amount,
                                  String currency, String status) {
        boolean isComplete() {
            return eventId != null && providerRef != null && paymentReference != null && amount != null
                    && currency != null && status != null;
        }
    }
}
