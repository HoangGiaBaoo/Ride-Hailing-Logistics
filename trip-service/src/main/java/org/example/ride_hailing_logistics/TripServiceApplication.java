package org.example.ride_hailing_logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Vong doi chuyen, matching va offer, kenh realtime STOMP (FR-TRIP, FR-MAT, FR-RT). */
@SpringBootApplication
public class TripServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripServiceApplication.class, args);
    }
}
