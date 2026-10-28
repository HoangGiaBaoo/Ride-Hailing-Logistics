package org.example.ride_hailing_logistics.pricing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.pricing.dto.Place;

public record QuoteRequest(@NotNull ServiceType serviceType, @NotNull VehicleType vehicleType,
                           @NotNull @Valid Place pickup, @NotNull @Valid Place dropoff) {}
