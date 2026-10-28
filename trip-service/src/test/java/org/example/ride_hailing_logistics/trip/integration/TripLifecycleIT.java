package org.example.ride_hailing_logistics.trip.integration;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.platform.kafka.KafkaTopics;
import org.example.ride_hailing_logistics.platform.outbox.EventEnvelope;
import org.example.ride_hailing_logistics.trip.dto.request.TransitionRequest;
import org.example.ride_hailing_logistics.trip.dto.response.OfferResponse;
import org.example.ride_hailing_logistics.trip.dto.response.TripResponse;
import org.example.ride_hailing_logistics.trip.enums.CancelReason;
import org.example.ride_hailing_logistics.trip.enums.DriverAvailability;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * UC-04 trong pham vi trip-service: hoan tat phat TripCompleted dung MOT lan va tra tai xe ve AVAILABLE;
 * FareFinalized (pricing-service) den qua Kafka that thi chuyen ghi cuoc cuoi. Huy chuyen tra tai xe ve AVAILABLE.
 */
class TripLifecycleIT extends TripIntegrationTest {

    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private KafkaTopics topics;
    @Autowired private ObjectMapper mapper;

    @Test
    void completedTripReleasesDriverAndEmitsTripCompletedOnce() throws Exception {
        UUID customer = newCustomer();
        UUID driverId = onlineDriverAt(10.7772, 106.7012);
        TripResponse trip = requestRide(customer);
        OfferResponse offer = await(() -> dispatchService.currentOfferFor(driverId).orElse(null), 10_000);
        assertNotNull(offer);
        dispatchService.accept(offer.offerId(), driverId);

        Actor driver = new Actor(driverId, Set.of(Role.DRIVER));
        Actor customerActor = new Actor(customer, Set.of(Role.CUSTOMER));
        String pin = tripService.get(trip.id(), customerActor).startPin();
        tripService.startPickup(trip.id(), driver, null);
        location.put(driverId, 10.7770, 106.7010);   // vi tri server: cach diem don ~15 m
        tripService.arrive(trip.id(), driver, null);
        tripService.start(trip.id(), driver, new TransitionRequest(null, null, pin, null));
        tripService.complete(trip.id(), driver, null);
        tripService.complete(trip.id(), driver, null);   // gui lap: khong phat TripCompleted lan hai

        assertEquals(DriverAvailability.AVAILABLE, availabilityOf(driverId));
        assertEquals(1, count("select count(*) from outbox_events where event_type = 'TripCompleted'"));
        assertEquals(1, count("select count(*) from trip_status_history where trip_id = ? and to_status = 'COMPLETED'", trip.id()));
        // Moi buoc cua state machine deu co dung mot dong lich su (FR-TRIP-008)
        assertEquals(7, count("select count(*) from trip_status_history where trip_id = ?", trip.id()));

        // pricing-service chot cuoc -> FareFinalized len Kafka -> inbox cua trip -> ghi cuoc cuoi
        publishFareFinalized(trip.id(), QUOTED_TOTAL);
        BigDecimal finalFare = await(() -> jdbc.queryForObject("select final_fare from trips where id = ?", BigDecimal.class, trip.id()), 30_000);
        assertNotNull(finalFare, "FareFinalized never reached the trip");
        assertEquals(0, QUOTED_TOTAL.compareTo(finalFare));
    }

    @Test
    void customerCancellingWhileMatchingReleasesTheOfferedDriver() throws Exception {
        UUID customer = newCustomer();
        UUID driverId = onlineDriverAt(10.7772, 106.7012);
        TripResponse trip = requestRide(customer);
        assertNotNull(await(() -> dispatchService.currentOfferFor(driverId).orElse(null), 10_000));
        assertEquals(DriverAvailability.OFFERED, availabilityOf(driverId));

        Actor customerActor = new Actor(customer, Set.of(Role.CUSTOMER));
        tripService.cancel(trip.id(), customerActor, CancelReason.CHANGED_PLANS, null, false);
        tripService.cancel(trip.id(), customerActor, CancelReason.CHANGED_PLANS, null, false);   // huy lap: idempotent

        // FR-CAN-005, FR-DRV-013: offer bi huy va tai xe tro lai AVAILABLE dung mot lan, khong ket OFFERED
        assertEquals(DriverAvailability.AVAILABLE, availabilityOf(driverId));
        assertEquals(0, count("select count(*) from driver_offers where status = 'PENDING'"));
        assertEquals(1, count("select count(*) from trip_status_history where trip_id = ? and to_status = 'CANCELLED'", trip.id()));
        assertEquals(1, count("select count(*) from outbox_events where event_type = 'TripCancelled'"));
    }

    @Test
    void suspensionEventTakesIdleDriverOffline() throws Exception {
        UUID driverId = onlineDriverAt(10.7772, 106.7012);
        String payload = mapper.writeValueAsString(Map.of("driverId", driverId, "reason", "test", "suspendedAt", Instant.now()));
        send("driver", "DriverSuspended", driverId, payload);
        // DriverSuspended (driver-service) -> trip dua tai xe dang ranh ve OFFLINE ngay (FR-DRV-014)
        assertNotNull(await(() -> availabilityOf(driverId) == DriverAvailability.OFFLINE ? Boolean.TRUE : null, 30_000));
    }

    private void publishFareFinalized(UUID tripId, BigDecimal total) throws Exception {
        send("pricing", "FareFinalized", tripId, mapper.writeValueAsString(Map.of("tripId", tripId, "total", total)));
    }

    private void send(String producer, String type, UUID aggregateId, String payload) throws Exception {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), type, 1, Instant.now(), null, producer,
                producer, aggregateId.toString(), payload);
        var record = new ProducerRecord<>(topics.events(producer), aggregateId.toString(), mapper.writeValueAsString(envelope));
        record.headers().add(KafkaTopics.HEADER_EVENT_TYPE, type.getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }
}
