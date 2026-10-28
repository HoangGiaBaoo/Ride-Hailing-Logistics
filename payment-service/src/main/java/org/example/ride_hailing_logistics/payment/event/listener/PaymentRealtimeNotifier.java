package org.example.ride_hailing_logistics.payment.event.listener;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.realtime.RealtimeBridge;
import org.example.ride_hailing_logistics.payment.event.local.PaymentUpdated;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.Map;

/** Bao khach ket qua thanh toan qua WebSocket cua trip-service, chi sau khi trang thai da duoc luu (NFR-REL-001). */
@Component
@RequiredArgsConstructor
public class PaymentRealtimeNotifier {

    private final RealtimeBridge realtime;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPayment(PaymentUpdated e) {
        realtime.notifyUser(e.customerId(), RealtimeBridge.QUEUE_NOTIFICATIONS, "PAYMENT_UPDATED",
                Map.of("paymentId", e.paymentId(), "tripId", e.tripId(), "status", e.status(),
                        "amount", e.amount(), "currency", e.currency()));
    }
}
