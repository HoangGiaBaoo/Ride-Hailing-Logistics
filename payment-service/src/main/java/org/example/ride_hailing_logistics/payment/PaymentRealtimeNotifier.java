package org.example.ride_hailing_logistics.payment;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.realtime.RealtimeBridge;
import org.example.ride_hailing_logistics.payment.PaymentService.PaymentUpdated;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** Bao khach ket qua thanh toan qua WebSocket cua trip-service, chi sau khi trang thai da duoc luu (NFR-REL-001). */
@Component
@RequiredArgsConstructor
class PaymentRealtimeNotifier {

    private final RealtimeBridge realtime;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPayment(PaymentUpdated e) {
        realtime.notifyUser(e.customerId(), RealtimeBridge.QUEUE_NOTIFICATIONS, "PAYMENT_UPDATED",
                Map.of("paymentId", e.paymentId(), "tripId", e.tripId(), "status", e.status(),
                        "amount", e.amount(), "currency", e.currency()));
    }
}
