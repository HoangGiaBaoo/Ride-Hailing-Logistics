package org.example.ride_hailing_logistics.trip.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.trip.enums.CancelReason;

public record CancelRequest(@NotNull CancelReason reasonCode, @Size(max = 500) String note) {}
