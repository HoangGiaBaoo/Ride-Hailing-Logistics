package org.example.ride_hailing_logistics.wallet;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/** DriverApproved (driver-service) -> mo vi cho tai xe (FR-WAL-001). Vi da co thi bo qua. */
@Component
@RequiredArgsConstructor
class DriverApprovedHandler implements EventHandler {

    private final WalletService walletService;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "wallet.open-wallet";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("DriverApproved");
    }

    @Override
    public void handle(EventEnvelope event) {
        walletService.openWallet(outbox.read(event, DriverApprovedView.class).driverId());
    }

    record DriverApprovedView(UUID driverId) {}
}
