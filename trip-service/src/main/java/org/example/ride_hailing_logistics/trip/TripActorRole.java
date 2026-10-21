package org.example.ride_hailing_logistics.trip;

import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;

/** Vai tro cua nguoi thuc hien MOT chuyen trang thai — dung de kiem quyen trong state machine (FR-TRIP-009). */
public enum TripActorRole {
    CUSTOMER,
    DRIVER,
    SUPPORT_STAFF,
    SYSTEM;

    /** Nguoi goi la khach cua chuyen, tai xe cua chuyen, hay nhan vien ho tro. Khong lien quan -> null. */
    static TripActorRole of(Actor actor, Trip trip) {
        if (actor.isSystem()) return SYSTEM;
        if (actor.id().equals(trip.getCustomerId()) && actor.has(Role.CUSTOMER)) return CUSTOMER;
        if (actor.id().equals(trip.getDriverId()) && actor.has(Role.DRIVER)) return DRIVER;
        if (actor.has(Role.SUPPORT_STAFF) || actor.has(Role.ADMIN)) return SUPPORT_STAFF;
        return null;
    }
}
