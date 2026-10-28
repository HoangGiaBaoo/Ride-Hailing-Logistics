package org.example.ride_hailing_logistics.driver.service;

import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.driver.dto.response.DocumentFileResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverDetailResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverSummaryResponse;
import org.example.ride_hailing_logistics.driver.enums.ReviewDecisionType;
import org.example.ride_hailing_logistics.driver.enums.ReviewStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Nhan vien kiem duyet xu ly ho so tai xe; quan tri vien dinh chi (FR-DRV-005, FR-DRV-006, UC-01). */
public interface DriverReviewService {

    PageResponse<DriverSummaryResponse> list(ReviewStatus status, Pageable pageable);

    DriverDetailResponse detail(UUID driverId);

    DocumentFileResponse documentFile(UUID documentId, Actor actor);

    DriverDetailResponse decide(UUID driverId, ReviewDecisionType decision, String reason, Actor actor);

    DriverDetailResponse suspend(UUID driverId, String reason, Actor actor);
}
