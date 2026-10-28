package org.example.ride_hailing_logistics.trip.dto.response;

import org.example.ride_hailing_logistics.trip.enums.DriverAvailability;

import java.util.List;

/** Trang thai kha dung + ly do chua duoc Online (UI-DRV-002). */
public record AvailabilityResponse(DriverAvailability availability, List<String> blockingReasons) {}
