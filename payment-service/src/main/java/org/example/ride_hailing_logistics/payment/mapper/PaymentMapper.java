package org.example.ride_hailing_logistics.payment.mapper;

import org.example.ride_hailing_logistics.payment.dto.response.CommissionRuleResponse;
import org.example.ride_hailing_logistics.payment.dto.response.PaymentResponse;
import org.example.ride_hailing_logistics.payment.dto.response.RefundResponse;
import org.example.ride_hailing_logistics.payment.dto.response.WalletEntryResponse;
import org.example.ride_hailing_logistics.payment.entity.CommissionRule;
import org.example.ride_hailing_logistics.payment.entity.Payment;
import org.example.ride_hailing_logistics.payment.entity.Refund;
import org.example.ride_hailing_logistics.payment.entity.WalletEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Entity -> DTO (MapStruct sinh code luc bien dich; truong thieu = loi bien dich, khong lo am tham). */
@Mapper
public interface PaymentMapper {

    RefundResponse toRefund(Refund refund);

    @Mapping(target = "refunds", source = "refunds")
    PaymentResponse toPayment(Payment payment, List<Refund> refunds);

    @Mapping(target = "type", source = "entryType")
    WalletEntryResponse toWalletEntry(WalletEntry entry);

    @Mapping(target = "version", source = "ruleVersion")
    CommissionRuleResponse toCommissionRule(CommissionRule rule);
}
