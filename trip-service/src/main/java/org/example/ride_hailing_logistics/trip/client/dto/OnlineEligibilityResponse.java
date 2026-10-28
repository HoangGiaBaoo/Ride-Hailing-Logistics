package org.example.ride_hailing_logistics.trip.client.dto;

import java.util.List;

/** Hop dong voi driver-service: du dieu kien Online chua, ly do cu the, va ban chup tai xe + xe. */
public record OnlineEligibilityResponse(boolean eligible, List<String> reasons, DriverSnapshotResponse snapshot) {}
