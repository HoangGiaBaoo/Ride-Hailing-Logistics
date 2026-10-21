package org.example.ride_hailing_logistics.driver;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/** UserRegistered (identity-service) -> mo ho so nhap cho tai khoan tai xe (FR-DRV-001). */
@Component
@RequiredArgsConstructor
class UserRegisteredHandler implements EventHandler {

    private final DriverProfileService profileService;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "driver.create-draft-profile";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("UserRegistered");
    }

    @Override
    public void handle(EventEnvelope event) {
        UserRegisteredView user = outbox.read(event, UserRegisteredView.class);
        if (user.role() == Role.DRIVER) {
            profileService.createDraftProfile(user.userId());   // da co ho so thi bo qua: idempotent
        }
    }

    record UserRegisteredView(UUID userId, Role role) {}
}
