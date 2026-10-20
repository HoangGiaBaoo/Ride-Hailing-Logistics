package org.example.ride_hailing_logistics.platform;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final AuditService auditService;
    private final OutboxService outboxService;

    /** Chi doc: khong co endpoint sua/xoa audit (FR-ADM-006). */
    @GetMapping("/api/v1/admin/audit")
    public ApiResponse<PageResponse<AuditService.AuditView>> audit(@RequestParam(required = false) String targetType,
                                                                  @RequestParam(required = false) String targetId,
                                                                  @RequestParam(required = false) UUID actorId,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditService.search(targetType, targetId, actorId,
                PageResponse.pageable(page, size, Sort.by("createdAt").descending())));
    }

    /** Event khong xu ly duoc sau so lan thu cho phep (FR-EVT-008). */
    @GetMapping("/api/v1/admin/outbox/failed")
    public ApiResponse<PageResponse<OutboxService.EventEnvelopeView>> failedEvents(@RequestParam(defaultValue = "0") int page,
                                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(outboxService.failed(PageResponse.pageable(page, size, Sort.unsorted())));
    }

    @PostMapping("/api/v1/admin/outbox/{eventId}/retry")
    public ApiResponse<Map<String, String>> retry(@PathVariable UUID eventId) {
        outboxService.retry(eventId, CurrentUser.actor());
        return ApiResponse.ok(Map.of("status", "REQUEUED"));
    }
}
