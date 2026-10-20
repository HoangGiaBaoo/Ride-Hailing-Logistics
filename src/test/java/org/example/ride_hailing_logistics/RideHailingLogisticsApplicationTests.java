package org.example.ride_hailing_logistics;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Ung dung khoi dong duoc: Flyway chay, Hibernate validate khop schema. Can PostgreSQL + Redis local. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class RideHailingLogisticsApplicationTests {

    @Test
    void contextLoads() {
    }
}
