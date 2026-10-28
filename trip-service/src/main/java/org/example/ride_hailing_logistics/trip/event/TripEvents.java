package org.example.ride_hailing_logistics.trip.event;

/** Ten event trip-service phat len topic rhl.trip.events (qua outbox). Them truong = them cuoi (NFR-COMP-002). */
public final class TripEvents {

    public static final String PRODUCER = "trip";
    public static final String TRIP_REQUESTED = "TripRequested";
    public static final String TRIP_ACCEPTED = "TripAccepted";
    public static final String TRIP_COMPLETED = "TripCompleted";
    public static final String TRIP_CANCELLED = "TripCancelled";
    public static final String DRIVER_AVAILABILITY_CHANGED = "DriverAvailabilityChanged";

    private TripEvents() {
    }
}
