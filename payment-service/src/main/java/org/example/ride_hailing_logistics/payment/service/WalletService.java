package org.example.ride_hailing_logistics.payment.service;

import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.payment.dto.PaymentSucceededView;
import org.example.ride_hailing_logistics.payment.dto.response.CommissionRuleResponse;
import org.example.ride_hailing_logistics.payment.dto.response.WalletEntryResponse;
import org.example.ride_hailing_logistics.payment.dto.response.WalletResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Vi tai xe, but toan, hoa hong (FR-WAL-001...009). So du = tong but toan, khong luu rieng. */
public interface WalletService {

    /** Goi lai nhieu lan van chi co mot vi (unique driver_id). */
    UUID openWallet(UUID driverId);

    /** Idempotent theo (vi, loai, paymentId) (FR-WAL-005). */
    void postPaymentEarnings(PaymentSucceededView payment);

    WalletResponse walletOf(UUID driverId);

    PageResponse<WalletEntryResponse> entriesOf(UUID driverId, Pageable pageable);

    WalletResponse adjust(UUID driverId, BigDecimal amount, String reason, String reference, Actor actor);

    List<CommissionRuleResponse> commissionRules();

    CommissionRuleResponse createCommissionRule(ServiceType type, BigDecimal rate, Instant effectiveFrom, Actor actor);
}
