package org.example.ride_hailing_logistics.wallet;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.security.CurrentUser;
import org.example.ride_hailing_logistics.wallet.WalletDtos.AdjustmentRequest;
import org.example.ride_hailing_logistics.wallet.WalletDtos.CommissionRuleRequest;
import org.example.ride_hailing_logistics.wallet.WalletDtos.CommissionRuleResponse;
import org.example.ride_hailing_logistics.wallet.WalletDtos.EntryResponse;
import org.example.ride_hailing_logistics.wallet.WalletDtos.WalletResponse;
import org.example.ride_hailing_logistics.platform.idempotency.IdempotencyService;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final IdempotencyService idempotency;

    @GetMapping("/api/v1/drivers/me/wallet")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<WalletResponse> myWallet() {
        return ApiResponse.ok(walletService.walletOf(CurrentUser.id()));
    }

    @GetMapping("/api/v1/drivers/me/wallet/entries")
    @PreAuthorize("hasRole('DRIVER')")
    public ApiResponse<PageResponse<EntryResponse>> myEntries(@RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(walletService.entriesOf(CurrentUser.id(), PageResponse.pageable(page, size, Sort.unsorted())));
    }

    @GetMapping("/api/v1/finance/wallets/{driverId}")
    @PreAuthorize("hasAnyRole('FINANCE_STAFF','ADMIN')")
    public ApiResponse<WalletResponse> wallet(@PathVariable UUID driverId) {
        return ApiResponse.ok(walletService.walletOf(driverId));
    }

    @GetMapping("/api/v1/finance/wallets/{driverId}/entries")
    @PreAuthorize("hasAnyRole('FINANCE_STAFF','ADMIN')")
    public ApiResponse<PageResponse<EntryResponse>> entries(@PathVariable UUID driverId,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(walletService.entriesOf(driverId, PageResponse.pageable(page, size, Sort.unsorted())));
    }

    @PostMapping("/api/v1/finance/wallets/{driverId}/adjustments")
    @PreAuthorize("hasRole('FINANCE_STAFF')")
    public ApiResponse<WalletResponse> adjust(@PathVariable UUID driverId,
                                              @RequestHeader("Idempotency-Key") String key,
                                              @Valid @RequestBody AdjustmentRequest request) {
        var actor = CurrentUser.actor();
        return ApiResponse.ok(idempotency.execute(actor.id(), "wallet.adjust:" + driverId, key, request, WalletResponse.class,
                () -> walletService.adjust(driverId, request.amount(), request.reason(), request.reference(), actor)));
    }

    @GetMapping("/api/v1/admin/commission-rules")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE_STAFF')")
    public ApiResponse<List<CommissionRuleResponse>> commissionRules() {
        return ApiResponse.ok(walletService.commissionRules());
    }

    @PostMapping("/api/v1/admin/commission-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CommissionRuleResponse> createCommissionRule(@Valid @RequestBody CommissionRuleRequest request) {
        return ApiResponse.ok(walletService.createCommissionRule(request.serviceType(), request.rate(),
                request.effectiveFrom(), CurrentUser.actor()));
    }
}
