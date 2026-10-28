package org.example.ride_hailing_logistics.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Cache Redis dung chung: khoa chuoi "rhl:cache:{ten}::{khoa}", gia tri JSON co kieu cu the cho tung cache
 * (khong dung default typing de tranh deserialize kieu tuy y). Moi service khai bao cache cua minh qua
 * {@link #jsonCache(Class, Duration)} trong RedisCacheManagerBuilderCustomizer.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        return base(Duration.ofMinutes(10));
    }

    public static <T> RedisCacheConfiguration jsonCache(Class<T> type, Duration ttl) {
        return base(ttl).serializeValuesWith(SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(type)));
    }

    private static RedisCacheConfiguration base(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .prefixCacheNameWith("rhl:cache:")
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));
    }
}
