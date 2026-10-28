package org.example.ride_hailing_logistics.pricing.config;

import org.example.ride_hailing_logistics.common.config.CacheConfig;
import org.example.ride_hailing_logistics.pricing.dto.ActiveRules;
import org.example.ride_hailing_logistics.pricing.service.PricingRuleService;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class PricingCacheConfig {

    /** Bang gia doi qua API admin (evict ngay); TTL chi de tu lam moi neu DB bi sua tay. */
    @Bean
    public RedisCacheManagerBuilderCustomizer pricingCaches() {
        return builder -> builder.withCacheConfiguration(PricingRuleService.CACHE,
                CacheConfig.jsonCache(ActiveRules.class, Duration.ofMinutes(10)));
    }
}
