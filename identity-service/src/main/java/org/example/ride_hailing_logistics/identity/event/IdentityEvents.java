package org.example.ride_hailing_logistics.identity.event;

/** Ten event identity-service phat len topic rhl.identity.events (qua outbox). */
public final class IdentityEvents {

    public static final String PRODUCER = "identity";
    public static final String USER_REGISTERED = "UserRegistered";
    public static final String USER_PROFILE_UPDATED = "UserProfileUpdated";
    public static final String USER_STATUS_CHANGED = "UserStatusChanged";

    private IdentityEvents() {
    }
}
