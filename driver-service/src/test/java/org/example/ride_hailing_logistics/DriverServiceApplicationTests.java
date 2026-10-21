package org.example.ride_hailing_logistics;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Ung dung khoi dong duoc; Flyway + ddl-auto=validate khop nhau tren database rieng cua service. */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class DriverServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
