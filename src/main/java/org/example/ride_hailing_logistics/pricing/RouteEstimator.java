package org.example.ride_hailing_logistics.pricing;

/** Lay quang duong va ETA cua tuyen (FR-PRI-005). Ban do that hay uoc tinh deu di qua interface nay (TBD-02). */
public interface RouteEstimator {

    RouteEstimate estimate(double fromLat, double fromLng, double toLat, double toLng);

    /** approximate = true: khong co tuyen that tu nha cung cap, dang dung uoc tinh fallback (FR-PRI-006). */
    record RouteEstimate(long distanceMeters, long durationSeconds, boolean approximate) {}
}
