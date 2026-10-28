package org.example.ride_hailing_logistics.trip.event.handler;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.trip.service.DriverStateService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** UserProfileUpdated (identity-service) -> cap nhat ten tai xe dung cho chuyen moi. */
@Component
@RequiredArgsConstructor
public class UserProfileUpdatedHandler implements EventHandler {

    private final DriverStateService driverStates;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "trip.user-profile-updated";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("UserProfileUpdated");
    }

    @Override
    public void handle(EventEnvelope event) {
        ProfilePayload p = outbox.read(event, ProfilePayload.class);
        driverStates.applyProfileName(p.userId(), p.fullName());
    }

    /** Hop dong voi identity-service; chi doc truong can dung. */
    public record ProfilePayload(UUID userId, String fullName, Instant updatedAt) {}
}
