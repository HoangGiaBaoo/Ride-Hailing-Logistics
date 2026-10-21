package org.example.ride_hailing_logistics.payment;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.payment.PaymentDtos.CallbackPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Xu ly ket qua tu nha cung cap (NFR-SEC-010, FR-PAY-004, FR-PAY-005):
 * 1. chu ky + timestamp hop le, 2. callback chua tung xu ly (chong replay),
 * 3. doi chieu payment / so tien / tien te, 4. hoi lai nha cung cap, 5. chi PENDING moi doi trang thai.
 */
@Slf4j
@Service
public class PaymentCallbackService {

    private final PaymentRepository payments;
    private final SandboxPaymentGateway gateway;
    private final PaymentService paymentService;
    private final ObjectMapper mapper;
    private final AppProperties.Payment props;
    private final Clock clock;

    public PaymentCallbackService(PaymentRepository payments, SandboxPaymentGateway gateway, PaymentService paymentService,
                                  ObjectMapper mapper, AppProperties appProperties, Clock clock) {
        this.payments = payments;
        this.gateway = gateway;
        this.paymentService = paymentService;
        this.mapper = mapper;
        this.props = appProperties.payment();
        this.clock = clock;
    }

    @Transactional
    public void handle(String body, String timestamp, String signature) {
        Instant now = clock.instant();
        if (!SandboxSignature.verify(props.sandbox().secret(), timestamp, body, signature, now, props.callbackTolerance())) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Chữ ký callback không hợp lệ.");
        }
        CallbackPayload payload;
        try {
            payload = mapper.readValue(body, CallbackPayload.class);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Callback sai định dạng.");
        }
        if (!payload.isComplete()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Callback thiếu trường bắt buộc.");   // DR-VAL-006
        }
        if (payments.recordCallback(payload.eventId(), now) == 0) {
            log.info("Duplicate payment callback {} ignored", payload.eventId());
            return;
        }
        Payment payment = payments.findByProviderRefForUpdate(payload.providerRef())
                .orElseThrow(() -> BusinessException.notFound("giao dịch"));
        if (!payment.getId().toString().equals(payload.paymentReference())
                || new BigDecimal(payload.amount()).compareTo(payment.getAmount()) != 0
                || !payment.getCurrency().equals(payload.currency())) {
            // Khong khop tien/tien te/giao dich: khong tin, khong ghi nhan (NFR-SEC-010)
            throw BusinessException.rule("Callback không khớp giao dịch.");
        }
        SandboxPaymentGateway.Charge verified = gateway.fetchStatus(payload.providerRef());
        if (verified == null || !verified.status().equals(payload.status())) {
            throw BusinessException.rule("Không xác minh được kết quả với nhà cung cấp.");
        }
        if ("SUCCEEDED".equals(payload.status())) {
            paymentService.markSucceeded(payment, now);
        } else {
            paymentService.markFailed(payment, "Nhà cung cấp từ chối giao dịch", now);
        }
    }
}
