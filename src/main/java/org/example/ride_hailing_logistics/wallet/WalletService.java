package org.example.ride_hailing_logistics.wallet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.wallet.WalletDtos.CommissionRuleResponse;
import org.example.ride_hailing_logistics.wallet.WalletDtos.EntryResponse;
import org.example.ride_hailing_logistics.wallet.WalletDtos.WalletResponse;
import org.example.ride_hailing_logistics.wallet.WalletEnums.EntryType;
import org.example.ride_hailing_logistics.wallet.WalletEnums.ReferenceType;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    public static final String EVENT_EARNING_POSTED = "DriverEarningPosted";

    private final WalletRepository wallets;
    private final WalletEntryRepository entries;
    private final CommissionRuleRepository commissionRules;
    private final OutboxService outbox;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    /** Goi khi tai xe duoc duyet. Goi lai nhieu lan van chi co mot vi (unique driver_id). */
    @Transactional
    public UUID openWallet(UUID driverId) {
        return wallets.findByDriverId(driverId)
                .orElseGet(() -> wallets.save(Wallet.open(driverId, props.pricing().currency(), clock.instant())))
                .getId();
    }

    /**
     * Ghi thu nhap va hoa hong cho mot thanh toan thanh cong. Idempotent theo (vi, loai, paymentId) (FR-WAL-005):
     * consumer chay lai, hoac admin xu ly lai event, cung khong ghi trung.
     */
    @Transactional
    public void postPaymentEarnings(PaymentSucceededView payment) {
        UUID walletId = openWallet(payment.driverId());
        Wallet wallet = wallets.findByDriverIdForUpdate(payment.driverId()).orElseThrow();
        String ref = payment.paymentId().toString();
        if (entries.existsByWalletIdAndEntryTypeAndReferenceId(walletId, EntryType.COMMISSION, ref)) {
            log.info("Earnings for payment {} already posted", ref);
            return;
        }
        CommissionRule rule = commissionRules.findEffectiveAt(payment.serviceType(), payment.succeededAt())
                .orElseThrow(() -> new IllegalStateException("No commission rule for " + payment.serviceType()));
        CommissionCalculator.Split split = CommissionCalculator.split(payment.amount(), rule.getRate());
        Instant now = clock.instant();
        boolean cash = "CASH".equals(payment.method());
        EntryType earningType = "CANCELLATION_FEE".equals(payment.kind()) ? EntryType.CANCELLATION_EARNING : EntryType.TRIP_EARNING;
        if (!cash) {
            entries.save(WalletEntry.post(walletId, earningType, split.gross(), wallet.getCurrency(), ReferenceType.PAYMENT,
                    ref, "Thu nhập chuyến " + payment.tripCode(), null, now));
        }
        // Tien mat: tai xe da cam tien, vi chi ghi no hoa hong -> so du duoc phep am cho loai nay (TBD-10)
        entries.save(WalletEntry.post(walletId, EntryType.COMMISSION, split.commission().negate(), wallet.getCurrency(),
                ReferenceType.PAYMENT, ref, "Hoa hồng " + rule.getRate().movePointRight(2).stripTrailingZeros().toPlainString()
                        + "% (quy tắc v" + rule.getRuleVersion() + ")", null, now));
        BigDecimal balance = entries.balanceOf(walletId);
        outbox.append("wallet", "Wallet", walletId, EVENT_EARNING_POSTED, 1, new EarningPostedPayload(
                payment.tripId(), walletId, payment.driverId(), cash ? split.commission().negate() : split.net(),
                split.gross(), split.commission(), rule.getRuleVersion(), balance, wallet.getCurrency(), now));
    }

    @Transactional(readOnly = true)
    public WalletResponse walletOf(UUID driverId) {
        Wallet wallet = wallets.findByDriverId(driverId).orElseThrow(() -> BusinessException.notFound("ví"));
        return new WalletResponse(wallet.getId(), driverId, wallet.getCurrency(), entries.balanceOf(wallet.getId()),
                wallet.getStatus().name());
    }

    @Transactional(readOnly = true)
    public PageResponse<EntryResponse> entriesOf(UUID driverId, Pageable pageable) {
        Wallet wallet = wallets.findByDriverId(driverId).orElseThrow(() -> BusinessException.notFound("ví"));
        return PageResponse.of(entries.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable), EntryResponse::from);
    }

    /** Dieu chinh bang but toan bu (FR-PAY-012); idempotent theo reference; khong duoc lam so du am (FR-WAL-009). */
    @Transactional
    public WalletResponse adjust(UUID driverId, BigDecimal amount, String reason, String reference, Actor actor) {
        if (amount.signum() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số tiền điều chỉnh phải khác 0.");
        }
        Wallet wallet = wallets.findByDriverIdForUpdate(driverId).orElseThrow(() -> BusinessException.notFound("ví"));
        if (entries.existsByWalletIdAndEntryTypeAndReferenceId(wallet.getId(), EntryType.ADJUSTMENT, reference)) {
            return walletOf(driverId);
        }
        BigDecimal balance = entries.balanceOf(wallet.getId());
        if (balance.add(amount).signum() < 0) {
            throw BusinessException.rule("Điều chỉnh này làm số dư ví âm (" + balance.add(amount).toPlainString() + " "
                    + wallet.getCurrency() + ").");
        }
        entries.save(WalletEntry.post(wallet.getId(), EntryType.ADJUSTMENT, amount, wallet.getCurrency(),
                ReferenceType.ADJUSTMENT, reference, reason, actor.id(), clock.instant()));
        audit.record(actor, "WALLET_ADJUSTED", "Wallet", wallet.getId(),
                Map.of("driverId", driverId, "amount", amount, "reason", reason, "reference", reference,
                        "balanceBefore", balance));
        return walletOf(driverId);
    }

    @Transactional(readOnly = true)
    public List<CommissionRuleResponse> commissionRules() {
        return commissionRules.findAllByOrderByServiceTypeAscEffectiveFromDesc().stream().map(CommissionRuleResponse::from).toList();
    }

    @Transactional
    public CommissionRuleResponse createCommissionRule(ServiceType type, BigDecimal rate, Instant effectiveFrom, Actor actor) {
        Instant now = clock.instant();
        if (effectiveFrom.isBefore(now.minusSeconds(60))) {
            // Khong ap dung hoi to: cac chuyen da tinh hoa hong theo quy tac cu giu nguyen
            throw BusinessException.rule("Thời điểm hiệu lực không được ở quá khứ.");
        }
        commissionRules.findEffectiveAt(type, effectiveFrom).ifPresent(old -> old.closeAt(effectiveFrom));
        CommissionRule rule = commissionRules.save(CommissionRule.create(type, rate, commissionRules.maxVersion(type) + 1,
                effectiveFrom, null, actor.id(), now));
        audit.record(actor, "COMMISSION_RULE_CREATED", "CommissionRule", rule.getId(),
                Map.of("serviceType", type, "rate", rate, "version", rule.getRuleVersion(), "effectiveFrom", effectiveFrom));
        return CommissionRuleResponse.from(rule);
    }

    /** Goc nhin cua mien wallet ve event PaymentSucceeded: chi cac truong can dung, bo qua truong la (NFR-COMP-002). */
    public record PaymentSucceededView(UUID paymentId, UUID tripId, String tripCode, String kind, UUID driverId,
                                       ServiceType serviceType, BigDecimal amount, String currency, String method,
                                       Instant succeededAt) {}

    public record EarningPostedPayload(UUID tripId, UUID walletId, UUID driverId, BigDecimal netEarning, BigDecimal gross,
                                       BigDecimal commission, int commissionRuleVersion, BigDecimal balance,
                                       String currency, Instant postedAt) {}
}
