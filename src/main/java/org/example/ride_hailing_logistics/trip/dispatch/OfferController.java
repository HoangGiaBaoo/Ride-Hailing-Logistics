package org.example.ride_hailing_logistics.trip.dispatch;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.platform.idempotency.IdempotencyService;
import org.example.ride_hailing_logistics.trip.TripQueries;
import org.example.ride_hailing_logistics.trip.TripDtos.TripResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@PreAuthorize("hasRole('DRIVER')")
@RequiredArgsConstructor
public class OfferController {

    private final DispatchService dispatchService;
    private final IdempotencyService idempotency;
    private final TripQueries tripQueries;

    /** Snapshot offer hien tai — dung khi app tai xe ket noi lai (FR-RT-005). */
    @GetMapping("/api/v1/drivers/me/offers/current")
    public ApiResponse<DispatchService.OfferView> current() {
        return ApiResponse.ok(dispatchService.currentOfferFor(CurrentUser.id()).orElse(null));
    }

    @PostMapping("/api/v1/offers/{offerId}/accept")
    public ApiResponse<TripResponse> accept(@PathVariable UUID offerId, @RequestHeader("Idempotency-Key") String key) {
        UUID me = CurrentUser.id();
        return ApiResponse.ok(idempotency.execute(me, "offer.accept:" + offerId, key, Map.of("offerId", offerId),
                TripResponse.class, () -> tripQueries.asDriver(dispatchService.accept(offerId, me))));
    }

    @PostMapping("/api/v1/offers/{offerId}/decline")
    public ApiResponse<Map<String, String>> decline(@PathVariable UUID offerId) {
        dispatchService.decline(offerId, CurrentUser.id());
        return ApiResponse.ok(Map.of("status", "DECLINED"));
    }
}
