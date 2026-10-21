package org.example.ride_hailing_logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Ho so, kiem duyet, trang thai kha dung cua tai xe; telemetry va chi muc Redis GEO (FR-DRV, FR-LOC). */
@SpringBootApplication
public class DriverServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriverServiceApplication.class, args);
    }
}
