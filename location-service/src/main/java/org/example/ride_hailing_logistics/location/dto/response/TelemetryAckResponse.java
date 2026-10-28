package org.example.ride_hailing_logistics.location.dto.response;

/** Phan hoi cho app tai xe: ban tin co duoc dung lam vi tri hien tai khong, va chu ky gui tiep theo. */
public record TelemetryAckResponse(boolean accepted, boolean historical, boolean suspicious, long nextIntervalSeconds) {}
