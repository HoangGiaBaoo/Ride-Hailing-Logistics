package org.example.ride_hailing_logistics.pricing.service;

import org.example.ride_hailing_logistics.pricing.dto.SurgeQuote;

/** Do cung/cau theo o geohash va cua so truot (FR-PRI-007, FR-PRI-008). */
public interface SurgeService {

    String cellOf(double lat, double lng);

    void recordDemand(double lat, double lng);

    SurgeQuote current(String cell);
}
