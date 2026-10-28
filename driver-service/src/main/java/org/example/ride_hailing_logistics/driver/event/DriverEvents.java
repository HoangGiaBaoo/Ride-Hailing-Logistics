package org.example.ride_hailing_logistics.driver.event;

/** Event driver-service phat len topic rhl.driver.events (qua outbox). */
public final class DriverEvents {

    public static final String PRODUCER = "driver";
    public static final String DRIVER_APPROVED = "DriverApproved";
    public static final String DRIVER_SUSPENDED = "DriverSuspended";

    private DriverEvents() {
    }
}
