package org.example.ride_hailing_logistics.payment.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.common.tx.AfterCommit;
import org.example.ride_hailing_logistics.payment.dto.response.PaymentResponse;
import org.example.ride_hailing_logistics.payment.entity.Payment;
import org.example.ride_hailing_logistics.payment.entity.Refund;
import org.example.ride_hailing_logistics.payment.event.PaymentEvents;
import org.example.ride_hailing_logistics.payment.event.PaymentFailed;
import org.example.ride_hailing_logistics.payment.event.local.PaymentUpdated;
import org.example.ride_hailing_logistics.payment.mapper.PaymentMapper;
import org.example.ride_hailing_logistics.payment.event.PaymentSucceeded;
import org.example.ride_hailing_logistics.payment.event.RefundSucceeded;
import org.example.ride_hailing_logistics.payment.gateway.SandboxPaymentGateway;
import org.example.ride_hailing_logistics.payment.repository.PaymentRepository;
import org.example.ride_hailing_logistics.payment.repository.RefundRepository;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.payment.service.PaymentService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final SandboxPaymentGateway gateway;
    private final PaymentService self;
    private final OutboxService outbox;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final AppProperties.Payment props;
    private final PaymentMapper mapper;
    private final Clock clock;

    public PaymentServiceImpl(PaymentRepository payments, RefundRepository refunds, SandboxPaymentGateway gateway,
                          @Lazy PaymentService self, OutboxService outbox, AuditService audit,
                          ApplicationEventPublisher events, PaymentMapper mapper, AppProperties appProperties,
                              Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.gateway = gateway;
        this.self = self;
        this.outbox = outbox;
        this.audit = audit;
        this.events = events;
        this.props = appProperties.payment();
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Tao thanh toan tu cuoc da chot. Idempotent theo (trip, loai): event den lan hai khong tao thanh toan thu hai
     * (FR-PAY-003, unique uq_payment_trip_kind chan not).
     */
    @Override
    @Transactional
    public void createForTrip(UUID tripId, String tripCode, Payment.Kind kind, UUID customerId, UUID driverId,
                              ServiceType serviceType, BigDecimal amount, String currency, PaymentMethod method) {
        if (payments.existsByTripIdAndKind(tripId, kind)) {
            log.info("Payment {} for trip {} already exists", kind, tripId);
            return;
        }
        Instant now = clock.instant();
        Payment payment = payments.saveAndFlush(Payment.create(tripId, tripCode, kind, customerId, driverId, serviceType,
                amount, currency, method, now));
        if (method == PaymentMethod.CASH) {
            if (kind == Payment.Kind.TRIP_FARE) {
                // Tai xe thu tien mat khi hoan tat chuyen: ghi nhan thanh cong de tinh hoa hong
                markSucceeded(payment, now);
            }
            // Phi huy cua chuyen tien mat: de PENDING = khoan phai thu (FR-PAY-008), khong tu tru vao ai
            return;
        }
        // Goi nha cung cap SAU commit: neu transaction rollback thi khong co giao dich treo o nha cung cap
        UUID id = payment.getId();
        AfterCommit.run(() -> self.submitToProvider(id));
    }

    // REQUIRES_NEW: ham nay duoc goi trong afterCommit, luc transaction cu van con gan vao thread
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitToProvider(UUID paymentId) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != Payment.Status.PENDING || payment.getProviderRef() != null
                || payment.getMethod() != PaymentMethod.SANDBOX_CARD) {
            return;
        }
        // Khoa idempotency theo (payment, lan thu): gui lai cung lan thu khong bi tru tien hai lan (FR-PAY-003)
        String ref = gateway.charge(paymentId, payment.getAmount(), payment.getCurrency(), paymentId + ":" + payment.getAttempts());
        payment.attachProviderRef(ref, clock.instant());
    }

    /** Goi trong transaction da khoa payment. */
    @Override
    public void markSucceeded(Payment payment, Instant now) {
        if (!payment.succeed(now)) return;
        outbox.append(PaymentEvents.PRODUCER, "Payment", payment.getId(), PaymentEvents.PAYMENT_SUCCEEDED, 1,
                new PaymentSucceeded(payment.getId(), payment.getTripId(), payment.getTripCode(),
                        payment.getKind().name(), payment.getCustomerId(), payment.getDriverId(),
                        payment.getServiceType().name(), payment.getAmount(), payment.getCurrency(),
                        payment.getMethod().name(), now));
        events.publishEvent(new PaymentUpdated(payment.getId(), payment.getTripId(), payment.getCustomerId(),
                payment.getStatus().name(), payment.getAmount(), payment.getCurrency()));
    }

    @Override
    public void markFailed(Payment payment, String reason, Instant now) {
        if (!payment.fail(reason, now)) return;
        outbox.append(PaymentEvents.PRODUCER, "Payment", payment.getId(), PaymentEvents.PAYMENT_FAILED, 1,
                new PaymentFailed(payment.getId(), payment.getTripId(), payment.getCustomerId(),
                        payment.getAmount(), payment.getCurrency(), reason, payment.getAttempts(), now));
        events.publishEvent(new PaymentUpdated(payment.getId(), payment.getTripId(), payment.getCustomerId(),
                payment.getStatus().name(), payment.getAmount(), payment.getCurrency()));
    }

    /** Khach thu lai thanh toan loi; chuyen da hoan tat khong bi hoan tac (FR-PAY-008). */
    @Override
    @Transactional
    public PaymentResponse retry(UUID paymentId, UUID customerId) {
        Payment payment = payments.findByIdForUpdate(paymentId)
                .filter(p -> p.getCustomerId().equals(customerId))
                .orElseThrow(() -> BusinessException.notFound("thanh toán"));
        payment.retry(props.maxAttempts(), clock.instant());
        AfterCommit.run(() -> self.submitToProvider(paymentId));
        return toResponse(payment);
    }

    /** Hoan tien toan phan/mot phan (FR-PAY-009...011). Idempotency-Key xu ly o controller. */
    @Override
    @Transactional
    public PaymentResponse refund(UUID paymentId, BigDecimal amount, String reason, Actor actor) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(() -> BusinessException.notFound("thanh toán"));
        if (!payment.isRefundable()) {
            throw BusinessException.rule("Chỉ hoàn tiền được giao dịch đã thu thành công.");
        }
        Instant now = clock.instant();
        BigDecimal before = payment.getRefundedAmount();
        payment.applyRefund(amount, now);
        String providerRef = payment.getMethod() == PaymentMethod.CASH ? "cash-refund" : gateway.refund(payment.getProviderRef(), amount);
        Refund refund = refunds.save(Refund.succeeded(paymentId, amount, payment.getCurrency(), reason, actor.id(), providerRef, now));
        outbox.append(PaymentEvents.PRODUCER, "Payment", paymentId, PaymentEvents.REFUND_SUCCEEDED, 1, new RefundSucceeded(
                refund.getId(), paymentId, payment.getTripId(), payment.getCustomerId(), amount, payment.getCurrency(), now));
        audit.record(actor, "PAYMENT_REFUNDED", "Payment", paymentId, Map.of("amount", amount, "reason", reason,
                "refundedBefore", before, "refundedAfter", payment.getRefundedAmount()));
        events.publishEvent(new PaymentUpdated(paymentId, payment.getTripId(), payment.getCustomerId(),
                payment.getStatus().name(), payment.getAmount(), payment.getCurrency()));
        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> forTrip(UUID tripId, Actor actor) {
        List<Payment> list = payments.findByTripIdOrderByCreatedAt(tripId);
        boolean staff = actor.has(Role.FINANCE_STAFF) || actor.has(Role.SUPPORT_STAFF) || actor.has(Role.ADMIN);
        if (!staff && list.stream().anyMatch(p -> !p.getCustomerId().equals(actor.id()))) {
            throw BusinessException.notFound("thanh toán");
        }
        return list.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(Payment.Status status, Pageable pageable) {
        var page = status == null ? payments.findAll(pageable) : payments.findByStatus(status, pageable);
        return PageResponse.of(page, this::toResponse);
    }

    /**
     * Doi soat: thanh toan PENDING chua gui duoc nha cung cap (tien trinh chet ngay sau commit) thi gui lai.
     * Nho vay su co tam thoi khong lam mat khoan thu (NFR-REL-007, FR-EVT-007).
     */
    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void reconcile() {
        try {
            for (UUID id : payments.findUnsubmitted(Payment.Status.PENDING, PaymentMethod.SANDBOX_CARD,
                    clock.instant().minusSeconds(10))) {
                self.submitToProvider(id);
            }
        } catch (RuntimeException e) {
            log.warn("Payment reconciliation failed: {}", e.getMessage());
        }
    }

    private PaymentResponse toResponse(Payment p) {
        return mapper.toPayment(p, refunds.findByPaymentIdOrderByCreatedAt(p.getId()));
    }
}
