package org.example.ride_hailing_logistics.location.config;

import org.example.ride_hailing_logistics.common.config.CacheConfig;
import org.example.ride_hailing_logistics.location.service.impl.LocationServiceImpl;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LocationCacheConfig {

    /** TTL ngan: event doi trang thai da evict, TTL chi la luoi an toan khi mat event. */
    @Bean
    public RedisCacheManagerBuilderCustomizer locationCaches() {
        return builder -> builder.withCacheConfiguration(LocationServiceImpl.CONTEXT_CACHE,
                CacheConfig.jsonCache(LocationServiceImpl.ContextView.class, Duration.ofSeconds(30)));
    }
}
