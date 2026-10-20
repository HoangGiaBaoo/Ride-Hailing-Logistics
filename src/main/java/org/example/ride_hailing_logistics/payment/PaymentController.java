package org.example.ride_hailing_logistics.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.payment.PaymentDtos.PaymentResponse;
import org.example.ride_hailing_logistics.payment.PaymentDtos.RefundRequest;
import org.example.ride_hailing_logistics.platform.idempotency.IdempotencyService;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentCallbackService callbackService;
    private final IdempotencyService idempotency;

    @GetMapping("/api/v1/trips/{tripId}/payments")
    @PreAuthorize("hasAnyRole('CUSTOMER','FINANCE_STAFF','SUPPORT_STAFF','ADMIN')")
    public ApiResponse<List<PaymentResponse>> forTrip(@PathVariable UUID tripId) {
        return ApiResponse.ok(paymentService.forTrip(tripId, CurrentUser.actor()));
    }

    @PostMapping("/api/v1/payments/{paymentId}/retry")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<PaymentResponse> retry(@PathVariable UUID paymentId, @RequestHeader("Idempotency-Key") String key) {
        UUID me = CurrentUser.id();
        return ApiResponse.ok(idempotency.execute(me, "payment.retry:" + paymentId, key, Map.of("paymentId", paymentId),
                PaymentResponse.class, () -> paymentService.retry(paymentId, me)));
    }

    /** Endpoint cong khai cho nha cung cap; bao ve bang chu ky HMAC + timestamp, khong bang JWT. */
    @PostMapping("/api/v1/payments/sandbox/callback")
    public ApiResponse<Map<String, String>> callback(@RequestHeader("X-Sandbox-Timestamp") String timestamp,
                                                     @RequestHeader("X-Sandbox-Signature") String signature,
                                                     @RequestBody String body) {
        callbackService.handle(body, timestamp, signature);
        return ApiResponse.ok(Map.of("received", "true"));
    }

    @GetMapping("/api/v1/finance/payments")
    @PreAuthorize("hasAnyRole('FINANCE_STAFF','ADMIN')")
    public ApiResponse<PageResponse<PaymentResponse>> list(@RequestParam(required = false) Payment.Status status,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(paymentService.list(status, PageResponse.pageable(page, size, Sort.by("createdAt").descending())));
    }

    @PostMapping("/api/v1/finance/payments/{paymentId}/refunds")
    @PreAuthorize("hasRole('FINANCE_STAFF')")
    public ApiResponse<PaymentResponse> refund(@PathVariable UUID paymentId, @RequestHeader("Idempotency-Key") String key,
                                               @Valid @RequestBody RefundRequest request) {
        Actor actor = CurrentUser.actor();
        return ApiResponse.ok(idempotency.execute(actor.id(), "payment.refund:" + paymentId, key, request,
                PaymentResponse.class, () -> paymentService.refund(paymentId, request.amount(), request.reason(), actor)));
    }
}
