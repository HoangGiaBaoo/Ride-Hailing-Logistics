package org.example.ride_hailing_logistics.trip.config;

import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.trip.domain.CancellationPolicy;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TripConfig {

    @Bean
    public CancellationPolicy cancellationPolicy(AppProperties props) {
        AppProperties.Trip trip = props.trip();
        return new CancellationPolicy(trip.freeCancelWindow(), trip.noShowWait(), trip.cancelFeeAfterAccept(),
                trip.cancelFeeArrived());
    }

    /**
     * Transaction lap trinh: luong co goi Feign (tao chuyen, dispatch, den diem don) phai goi service khac
     * NGOAI transaction — giu khoa hang DB trong luc cho mang la nghen ca he thong.
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    /** Khoa phan tan cho dispatch: nhieu instance trip-service cung quet thi chi mot instance xu ly mot chuyen. */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.data.redis.host:localhost}") String host,
                                         @Value("${spring.data.redis.port:6379}") int port,
                                         @Value("${spring.data.redis.password:}") String password) {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer().setAddress("redis://" + host + ":" + port)
                .setConnectionMinimumIdleSize(2).setConnectionPoolSize(8);
        if (!password.isBlank()) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}
