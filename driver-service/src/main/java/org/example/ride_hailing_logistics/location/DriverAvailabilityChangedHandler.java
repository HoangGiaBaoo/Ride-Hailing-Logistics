package org.example.ride_hailing_logistics.location;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.driver.DriverAvailabilityService;
import org.example.ride_hailing_logistics.driver.DriverAvailabilityService.AvailabilityChangedPayload;
import org.example.ride_hailing_logistics.driver.DriverEnums.DriverAvailability;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.platform.outbox.EventHandler;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Component;

import java.util.Set;

/** DriverAvailabilityChanged -> tai xe Offline/dinh chi bi xoa khoi chi muc khong gian (SRS §5.6, FR-LOC-008). */
@Component
@RequiredArgsConstructor
class DriverAvailabilityChangedHandler implements EventHandler {

    private final LocationService locationService;
    private final OutboxService outbox;

    @Override
    public String name() {
        return "location.availability-changed";
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of(DriverAvailabilityService.EVENT_AVAILABILITY_CHANGED);
    }

    @Override
    public void handle(EventEnvelope event) {
        AvailabilityChangedPayload payload = outbox.read(event, AvailabilityChangedPayload.class);
        if (payload.newStatus() == DriverAvailability.OFFLINE) {
            locationService.removeDriver(payload.driverId());
        }
    }
}
