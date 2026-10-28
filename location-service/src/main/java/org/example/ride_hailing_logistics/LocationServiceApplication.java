package org.example.ride_hailing_logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Nhan telemetry tai xe, chi muc Redis GEO, tim tai xe gan, dem nguon cung cho surge (FR-LOC-001..013). */
@SpringBootApplication
public class LocationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocationServiceApplication.class, args);
    }
}
