package org.example.ride_hailing_logistics.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(AppProperties.class)
public class CoreConfig {

    // Tiem Clock thay vi goi Instant.now() rai rac: test co dinh duoc thoi gian, server luon UTC (DR-007)
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
