package org.example.ride_hailing_logistics.payment;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.util.Secrets;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nha cung cap thanh toan GIA LAP (A-06, TBD-09). Hanh xu nhu cong that:
 * - charge() tra providerRef ngay, ket qua bao ve bat dong bo qua callback co chu ky HMAC
 * - fetchStatus() de he thong xac minh lai truoc khi ghi nhan (FR-PAY-004)
 * - cung idempotencyKey -> cung giao dich, khong thu hai lan (FR-PAY-003)
 */
@Slf4j
@Component
public class SandboxPaymentGateway {

    private final AppProperties.Sandbox props;
    private final ObjectMapper mapper;
    private final TaskScheduler scheduler;
    private final PaymentCallbackService callbackService;
    private final Clock clock;
    private final Map<String, Charge> chargesByRef = new ConcurrentHashMap<>();
    private final Map<String, String> refByIdempotencyKey = new ConcurrentHashMap<>();

    public SandboxPaymentGateway(AppProperties appProperties, ObjectMapper mapper, TaskScheduler scheduler,
                                 @Lazy PaymentCallbackService callbackService, Clock clock) {
        this.props = appProperties.payment().sandbox();
        this.mapper = mapper;
        this.scheduler = scheduler;
        this.callbackService = callbackService;
        this.clock = clock;
    }

    public String charge(UUID paymentId, BigDecimal amount, String currency, String idempotencyKey) {
        String existing = refByIdempotencyKey.get(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        String ref = "sbx_" + Secrets.randomCode(16);
        String status = props.forceFailure() ? "FAILED" : "SUCCEEDED";
        chargesByRef.put(ref, new Charge(ref, paymentId, amount, currency, status));
        refByIdempotencyKey.put(idempotencyKey, ref);
        scheduler.schedule(() -> sendCallback(ref), clock.instant().plus(props.callbackDelay()));
        return ref;
    }

    public String refund(String providerRef, BigDecimal amount) {
        if (!chargesByRef.containsKey(providerRef)) {
            throw new IllegalStateException("Unknown charge " + providerRef);
        }
        return "sbx_rf_" + Secrets.randomCode(12);
    }

    /** Nguon su that cua nha cung cap; he thong goi lai de doi chieu truoc khi tin callback. */
    public Charge fetchStatus(String providerRef) {
        return chargesByRef.get(providerRef);
    }

    private void sendCallback(String ref) {
        Charge c = chargesByRef.get(ref);
        if (c == null) return;
        String body = mapper.writeValueAsString(Map.of(
                "eventId", "evt_" + Secrets.randomCode(16),
                "providerRef", c.providerRef(),
                "paymentReference", c.paymentId().toString(),
                "amount", c.amount().toPlainString(),
                "currency", c.currency(),
                "status", c.status()));
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        String signature = Secrets.hmacSha256Hex(props.secret(), timestamp + "." + body);
        try {
            // Di dung duong nhu callback HTTP that: kiem chu ky, timestamp, replay, doi chieu so tien
            callbackService.handle(body, timestamp, signature);
        } catch (RuntimeException e) {
            log.warn("Sandbox callback for {} rejected: {}", ref, e.getMessage());
        }
    }

    public record Charge(String providerRef, UUID paymentId, BigDecimal amount, String currency, String status) {}
}
