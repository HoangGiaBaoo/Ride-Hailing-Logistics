package org.example.ride_hailing_logistics.trip;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.trip.dispatch.DispatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API noi bo: trip-service la nguon su that ve viec tai xe dang duoc giu cho offer/chuyen nao. */
@RestController
@RequestMapping("/internal/drivers")
@RequiredArgsConstructor
public class InternalTripController {

    private final DispatchService dispatchService;
    private final TripQueries tripQueries;

    @GetMapping("/{driverId}/engagement")
    public ApiResponse<Engagement> engagement(@PathVariable UUID driverId) {
        return ApiResponse.ok(new Engagement(dispatchService.pendingOfferIdOf(driverId).orElse(null),
                tripQueries.activeTripIdOf(driverId).orElse(null)));
    }

    public record Engagement(UUID pendingOfferId, UUID activeTripId) {}
}
