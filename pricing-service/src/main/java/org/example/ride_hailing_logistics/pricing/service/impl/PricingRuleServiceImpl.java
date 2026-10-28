package org.example.ride_hailing_logistics.pricing.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.pricing.dto.ActiveRules;
import org.example.ride_hailing_logistics.pricing.dto.request.PricingRuleRequest;
import org.example.ride_hailing_logistics.pricing.dto.response.PricingRuleResponse;
import org.example.ride_hailing_logistics.pricing.entity.PricingRule;
import org.example.ride_hailing_logistics.pricing.mapper.PricingMapper;
import org.example.ride_hailing_logistics.pricing.repository.PricingRuleRepository;
import org.example.ride_hailing_logistics.pricing.service.PricingRuleService;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingRuleServiceImpl implements PricingRuleService {

    private final PricingRuleRepository rules;
    private final PricingMapper mapper;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<PricingRuleResponse> list() {
        return rules.findAllByOrderByServiceTypeAscVehicleTypeAscRuleVersionDesc().stream().map(mapper::toResponse).toList();
    }

    // Doi bang gia hiem: xoa ca cache cho don gian va chac chan khong con gia cu
    @Override
    @Transactional
    @CacheEvict(cacheNames = CACHE, allEntries = true)
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
        PricingRuleResponse response = mapper.toResponse(rule);
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("rule", response);
        delta.put("reason", req.reason());
        audit.record(actor, "PRICING_RULE_CREATED", "PricingRule", rule.getId(), delta);
        return response;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CACHE, allEntries = true)
    public PricingRuleResponse deactivate(UUID ruleId, String reason, Actor actor) {
        PricingRule rule = rules.findById(ruleId).orElseThrow(() -> BusinessException.notFound("quy tắc giá"));
        rule.deactivate();
        audit.record(actor, "PRICING_RULE_DEACTIVATED", "PricingRule", ruleId, Map.of("reason", reason));
        return mapper.toResponse(rule);
    }

    // Cache theo pham vi, loc theo thoi diem o phia goi: quy tac hen gio hieu luc sau van dung khi toi gio
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE, key = "#serviceType + ':' + #vehicleType + ':' + #regionCode")
    public ActiveRules activeRules(ServiceType serviceType, VehicleType vehicleType, String regionCode) {
        return new ActiveRules(rules.findActive(serviceType, vehicleType, regionCode).stream()
                .map(mapper::toActiveRule).toList());
    }
}
