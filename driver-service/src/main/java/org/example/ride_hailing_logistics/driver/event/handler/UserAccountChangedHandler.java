package org.example.ride_hailing_logistics.driver.event.handler;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.driver.service.DriverProfileService;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Dong bo ho ten va trang thai tai khoan tu identity: dieu kien Online can tai khoan con hoat dong (BR-001). */
@Component
@RequiredArgsConstructor
public class UserAccountChangedHandler implements EventHandler {

    private final DriverProfileService profileService;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "driver.sync-account";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("UserProfileUpdated", "UserStatusChanged");
    }

    @Override
    public void handle(EventEnvelope event) {
        AccountView view = outbox.read(event, AccountView.class);
        Boolean active = view.status() == null ? null : "ACTIVE".equals(view.status());
        Instant at = view.updatedAt() != null ? view.updatedAt() : view.changedAt();
        profileService.syncAccount(view.userId(), view.fullName(), active, at);
    }

    record AccountView(UUID userId, String fullName, String status, Instant updatedAt, Instant changedAt) {}
}
