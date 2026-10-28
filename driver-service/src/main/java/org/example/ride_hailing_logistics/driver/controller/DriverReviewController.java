package org.example.ride_hailing_logistics.driver.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.driver.dto.request.ReviewDecisionRequest;
import org.example.ride_hailing_logistics.driver.dto.request.SuspendRequest;
import org.example.ride_hailing_logistics.driver.dto.response.DocumentFileResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverDetailResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverSummaryResponse;
import org.example.ride_hailing_logistics.driver.enums.ReviewStatus;
import org.example.ride_hailing_logistics.driver.service.DriverReviewService;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DriverReviewController {

    private final DriverReviewService reviewService;

    @GetMapping("/api/v1/review/drivers")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public ApiResponse<PageResponse<DriverSummaryResponse>> list(@RequestParam(defaultValue = "PENDING_REVIEW") ReviewStatus status,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reviewService.list(status, PageResponse.pageable(page, size, Sort.by("submittedAt"))));
    }

    @GetMapping("/api/v1/review/drivers/{driverId}")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public ApiResponse<DriverDetailResponse> detail(@PathVariable UUID driverId) {
        return ApiResponse.ok(reviewService.detail(driverId));
    }

    @GetMapping("/api/v1/review/documents/{documentId}/file")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public ResponseEntity<byte[]> documentFile(@PathVariable UUID documentId) {
        DocumentFileResponse file = reviewService.documentFile(documentId, CurrentUser.actor());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .body(file.content());
    }

    @PostMapping("/api/v1/review/drivers/{driverId}/decisions")
    @PreAuthorize("hasRole('REVIEWER')")
    public ApiResponse<DriverDetailResponse> decide(@PathVariable UUID driverId, @Valid @RequestBody ReviewDecisionRequest request) {
        return ApiResponse.ok(reviewService.decide(driverId, request.decision(), request.reason(), CurrentUser.actor()));
    }

    @PostMapping("/api/v1/admin/drivers/{driverId}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DriverDetailResponse> suspend(@PathVariable UUID driverId, @Valid @RequestBody SuspendRequest request) {
        return ApiResponse.ok(reviewService.suspend(driverId, request.reason(), CurrentUser.actor()));
    }
}
