package org.example.ride_hailing_logistics.pricing.mapper;

import org.example.ride_hailing_logistics.pricing.dto.ActiveRule;
import org.example.ride_hailing_logistics.pricing.dto.response.PricingRuleResponse;
import org.example.ride_hailing_logistics.pricing.dto.response.QuoteSnapshotResponse;
import org.example.ride_hailing_logistics.pricing.entity.FareQuote;
import org.example.ride_hailing_logistics.pricing.entity.PricingRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PricingMapper {

    @Mapping(target = "version", source = "ruleVersion")
    PricingRuleResponse toResponse(PricingRule rule);

    ActiveRule toActiveRule(PricingRule rule);

    @Mapping(target = "quoteId", source = "id")
    QuoteSnapshotResponse toSnapshot(FareQuote quote);
}
