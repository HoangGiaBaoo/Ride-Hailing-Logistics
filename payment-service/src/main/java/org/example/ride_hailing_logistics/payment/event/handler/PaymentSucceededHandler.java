package org.example.ride_hailing_logistics.payment.event.handler;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.payment.dto.PaymentSucceededView;
import org.example.ride_hailing_logistics.payment.service.WalletService;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;
import java.util.Set;

/** PaymentSucceeded -> ghi vi tai xe (SRS §5.6). */
@Component
@RequiredArgsConstructor
public class PaymentSucceededHandler implements EventHandler {

    private final WalletService walletService;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "wallet.post-earnings";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("PaymentSucceeded");
    }

    @Override
    public void handle(EventEnvelope event) {
        PaymentSucceededView payment = outbox.read(event, PaymentSucceededView.class);
        if (payment.driverId() == null) {
            return;   // phi huy truoc khi co tai xe: khong co ai de ghi thu nhap
        }
        walletService.postPaymentEarnings(payment);
    }
}
