package org.example.ride_hailing_logistics.trip.client.dto;

public record TelemetryAckResponse(boolean accepted, boolean historical, boolean suspicious, long nextIntervalSeconds) {}
