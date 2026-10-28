package org.example.ride_hailing_logistics.trip.service;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.trip.entity.Trip;
import org.example.ride_hailing_logistics.trip.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Truy van chi doc dung cho WebSocket (kiem quyen subscribe). */
@Service
@RequiredArgsConstructor
public class TripQueries {

    private final TripRepository trips;
    private final AppProperties props;
    private final Clock clock;

    /**
     * Ai duoc nghe kenh realtime cua chuyen (FR-RT-001, FR-RT-008, NFR-PRV-002):
     * khach va tai xe cua chuyen, nhan vien ho tro; chuyen da ket thuc qua thoi gian an han thi thoi.
     */
    @Transactional(readOnly = true)
    public boolean canSubscribe(UUID tripId, Actor actor) {
        Trip trip = trips.findById(tripId).orElse(null);
        if (trip == null) return false;
        boolean participant = actor.id().equals(trip.getCustomerId()) || actor.id().equals(trip.getDriverId());
        boolean staff = actor.has(Role.SUPPORT_STAFF) || actor.has(Role.ADMIN);
        if (!participant && !staff) return false;
        if (trip.getEndedAt() == null) return true;
        Instant graceEnd = trip.getEndedAt().plus(props.trip().realtimeGracePeriod());
        return clock.instant().isBefore(graceEnd);
    }
}
