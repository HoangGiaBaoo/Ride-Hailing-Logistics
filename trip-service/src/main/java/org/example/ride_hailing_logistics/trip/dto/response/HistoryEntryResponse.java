package org.example.ride_hailing_logistics.trip.dto.response;

import org.example.ride_hailing_logistics.trip.enums.TripActorRole;
import org.example.ride_hailing_logistics.trip.enums.TripStatus;
import java.time.Instant;

public record HistoryEntryResponse(TripStatus from, TripStatus to, TripActorRole actorRole, String reason, Instant at) {}
