package org.example.ride_hailing_logistics.pricing.service;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.pricing.dto.ActiveRules;
import org.example.ride_hailing_logistics.pricing.dto.request.PricingRuleRequest;
import org.example.ride_hailing_logistics.pricing.dto.response.PricingRuleResponse;

import java.util.List;
import java.util.UUID;

/** Quan tri bang gia (FR-PRI-001...004, FR-PRI-012, UI-ADM-004). */
public interface PricingRuleService {

    String CACHE = "pricing-rules";

    List<PricingRuleResponse> list();

    PricingRuleResponse create(PricingRuleRequest request, Actor actor);

    PricingRuleResponse deactivate(UUID ruleId, String reason, Actor actor);

    /** Quy tac dang bat cua mot pham vi, sap version giam dan; doc qua cache Redis. */
    ActiveRules activeRules(ServiceType serviceType, VehicleType vehicleType, String regionCode);
}
