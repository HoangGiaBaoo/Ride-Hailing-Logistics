package org.example.ride_hailing_logistics.pricing;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.PricingRuleRequest;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.PricingRuleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Quan tri bang gia (FR-PRI-001...004, FR-PRI-012, UI-ADM-004). */
@Service
@RequiredArgsConstructor
public class PricingRuleService {

    private final PricingRuleRepository rules;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> list() {
        return rules.findAllByOrderByServiceTypeAscVehicleTypeAscRuleVersionDesc().stream().map(PricingRuleResponse::from).toList();
    }

    @Transactional
    public PricingRuleResponse create(PricingRuleRequest req, Actor actor) {
        if (!req.vehicleType().supports(req.serviceType())) {
            throw BusinessException.rule("Loại xe không phục vụ dịch vụ này.");
        }
        if (props.regions().stream().noneMatch(r -> r.code().equals(req.regionCode()))) {
            throw BusinessException.rule("Vùng " + req.regionCode() + " chưa được cấu hình.");
        }
        if (req.effectiveTo() != null && !req.effectiveTo().isAfter(req.effectiveFrom())) {
            throw BusinessException.rule("Thời điểm kết thúc phải sau thời điểm bắt đầu hiệu lực.");
        }
        if (req.effectiveFrom().isBefore(clock.instant().minusSeconds(60))) {
            throw BusinessException.rule("Không tạo quy tắc giá hiệu lực hồi tố.");
        }
        List<PricingRule> overlapping = rules.findOverlapping(req.serviceType(), req.vehicleType(), req.regionCode(),
                req.effectiveFrom(), req.effectiveTo());
        if (!overlapping.isEmpty()) {
            // Hai quy tac cung pham vi cung thoi gian -> khong xac dinh duoc gia nao dung (FR-PRI-004)
            throw new BusinessException(ErrorCode.CONFLICT, "Quy tắc giá chồng lấn thời gian hiệu lực với quy tắc đang có.",
                    Map.of("conflictingRuleIds", overlapping.stream().map(PricingRule::getId).toList()));
        }
        PricingRule rule = rules.save(PricingRule.create(req.serviceType(), req.vehicleType(), req.regionCode(),
                req.baseFare(), req.perKm(), req.perMinute(), req.minimumFare(), req.bookingFee(), props.pricing().currency(),
                rules.maxVersion(req.serviceType(), req.vehicleType(), req.regionCode()) + 1,
                req.effectiveFrom(), req.effectiveTo(), actor.id(), clock.instant()));
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("rule", PricingRuleResponse.from(rule));
        delta.put("reason", req.reason());
        audit.record(actor, "PRICING_RULE_CREATED", "PricingRule", rule.getId(), delta);
        return PricingRuleResponse.from(rule);
    }

    @Transactional
    public PricingRuleResponse deactivate(UUID ruleId, String reason, Actor actor) {
        PricingRule rule = rules.findById(ruleId).orElseThrow(() -> BusinessException.notFound("quy tắc giá"));
        rule.deactivate();
        audit.record(actor, "PRICING_RULE_DEACTIVATED", "PricingRule", ruleId, Map.of("reason", reason));
        return PricingRuleResponse.from(rule);
    }
}
