package org.example.ride_hailing_logistics;

import org.example.ride_hailing_logistics.location.service.impl.LocationServiceImpl;
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
class LocationServiceApplicationTests {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void contextLoads() {
    }

    /** Redisson keo theo JCache: thieu spring.cache.type=redis thi cache ngu canh tai xe khong ton tai. */
    @Test
    void driverContextCacheIsBackedByRedis() {
        assertInstanceOf(RedisCacheManager.class, cacheManager);
        assertNotNull(cacheManager.getCache(LocationServiceImpl.CONTEXT_CACHE));
    }
}
