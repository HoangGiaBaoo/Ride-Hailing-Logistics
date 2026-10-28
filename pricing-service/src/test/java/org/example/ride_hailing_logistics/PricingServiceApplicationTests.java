package org.example.ride_hailing_logistics;

import org.example.ride_hailing_logistics.pricing.service.PricingRuleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Ung dung khoi dong duoc; Flyway + ddl-auto=validate khop nhau tren database rieng cua service. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class PricingServiceApplicationTests {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void contextLoads() {
    }

    /** Redisson keo theo JCache: thieu spring.cache.type=redis thi Boot chon JCache va tao quy tac gia loi 500. */
    @Test
    void pricingRulesCacheIsBackedByRedis() {
        assertInstanceOf(RedisCacheManager.class, cacheManager);
        assertNotNull(cacheManager.getCache(PricingRuleService.CACHE));
    }
}
