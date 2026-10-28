package org.example.ride_hailing_logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Vong doi chuyen, ghep chuyen, kha dung tai xe va kenh STOMP (FR-TRIP, FR-MAT, FR-DRV-010..013, FR-RT).
 * Kha dung tai xe nam o day cung bang trips: giu tai xe va gan chuyen phai trong MOT transaction (CON-06).
 */
@SpringBootApplication
public class TripServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripServiceApplication.class, args);
    }
}
