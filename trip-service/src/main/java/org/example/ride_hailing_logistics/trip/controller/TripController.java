package org.example.ride_hailing_logistics.trip.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.platform.idempotency.IdempotencyService;
import org.example.ride_hailing_logistics.trip.dto.request.CancelRequest;
import org.example.ride_hailing_logistics.trip.dto.request.CreateTripRequest;
import org.example.ride_hailing_logistics.trip.dto.request.TransitionRequest;
import org.example.ride_hailing_logistics.trip.dto.response.CancellationPreviewResponse;
import org.example.ride_hailing_logistics.trip.dto.response.HistoryEntryResponse;
import org.example.ride_hailing_logistics.trip.dto.response.TripResponse;
import org.example.ride_hailing_logistics.trip.enums.TripStatus;
import org.example.ride_hailing_logistics.trip.service.TripService;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final IdempotencyService idempotency;

    @PostMapping("/api/v1/trips")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TripResponse> create(@RequestHeader("Idempotency-Key") String key,
                                            @Valid @RequestBody CreateTripRequest request) {
        UUID me = CurrentUser.id();
        // Gui lap cung khoa + payload -> cung chuyen, khong tao chuyen moi (FR-TRIP-002)
        return ApiResponse.ok(idempotency.execute(me, "trip.create", key, request, TripResponse.class,
                () -> tripService.create(me, key, request)));
    }

    @GetMapping("/api/v1/trips/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<TripResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(tripService.get(id, CurrentUser.actor()));
    }

    @GetMapping("/api/v1/trips/{id}/history")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<HistoryEntryResponse>> history(@PathVariable UUID id) {
        return ApiResponse.ok(tripService.history(id, CurrentUser.actor()));
    }

    @GetMapping("/api/v1/trips/mine")
    @PreAuthorize("hasAnyRole('CUSTOMER','DRIVER')")
    public ApiResponse<PageResponse<TripResponse>> mine(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        Actor actor = CurrentUser.actor();
        boolean asDriver = actor.has(Role.DRIVER) && !actor.has(Role.CUSTOMER);
        return ApiResponse.ok(tripService.mine(actor, asDriver,
                PageResponse.pageable(page, size, Sort.by("createdAt").descending())));
    }

    @GetMapping("/api/v1/trips/{id}/cancellation-preview")
    @PreAuthorize("hasAnyRole('CUSTOMER','DRIVER')")
    public ApiResponse<CancellationPreviewResponse> previewCancel(@PathVariable UUID id) {
        return ApiResponse.ok(tripService.previewCancellation(id, CurrentUser.actor()));
    }

    @PostMapping("/api/v1/trips/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','DRIVER')")
    public ApiResponse<TripResponse> cancel(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key,
                                            @Valid @RequestBody CancelRequest request) {
        Actor actor = CurrentUser.actor();
        return ApiResponse.ok(idempotency.execute(actor.id(), "trip.cancel:" + id, key, request, TripResponse.class,
                () -> tripService.cancel(id, actor, request.reasonCode(), request.note(), false)));
    }

    @PostMapping("/api/v1/trips/{id}/pickup-started")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<TripResponse> pickupStarted(@PathVariable UUID id, @Valid @RequestBody(required = false) TransitionRequest request) {
        return ApiResponse.ok(tripService.startPickup(id, CurrentUser.actor(), request));
    }

    @PostMapping("/api/v1/trips/{id}/arrived")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<TripResponse> arrived(@PathVariable UUID id, @Valid @RequestBody(required = false) TransitionRequest request) {
        return ApiResponse.ok(tripService.arrive(id, CurrentUser.actor(), request));
    }

    @PostMapping("/api/v1/trips/{id}/started")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<TripResponse> started(@PathVariable UUID id, @Valid @RequestBody(required = false) TransitionRequest request) {
        return ApiResponse.ok(tripService.start(id, CurrentUser.actor(), request));
    }

    @PostMapping("/api/v1/trips/{id}/completed")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<TripResponse> completed(@PathVariable UUID id, @Valid @RequestBody(required = false) TransitionRequest request) {
        return ApiResponse.ok(tripService.complete(id, CurrentUser.actor(), request));
    }

    // ---------------- nhan vien ho tro (FR-ADM-001, FR-CAN-007)

    @GetMapping("/api/v1/support/trips")
    @PreAuthorize("hasAnyRole('SUPPORT_STAFF','ADMIN')")
    public ApiResponse<PageResponse<TripResponse>> search(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) ServiceType serviceType,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(tripService.search(code, customerId, driverId, serviceType, status, from, to,
                PageResponse.pageable(page, size, Sort.by("createdAt").descending())));
    }

    @PostMapping("/api/v1/support/trips/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPPORT_STAFF','ADMIN')")
    public ApiResponse<TripResponse> supportCancel(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key,
                                                   @Valid @RequestBody CancelRequest request) {
        Actor actor = CurrentUser.actor();
        return ApiResponse.ok(idempotency.execute(actor.id(), "trip.support-cancel:" + id, key, request, TripResponse.class,
                () -> tripService.cancel(id, actor, request.reasonCode(), request.note(), true)));
    }
}
