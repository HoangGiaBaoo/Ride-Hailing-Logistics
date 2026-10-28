package org.example.ride_hailing_logistics.location.dto.response;

/** So tai xe AVAILABLE co vi tri moi trong o geohash: nguon cung cho surge (FR-PRI-008). */
public record SupplyResponse(String cell, long availableDrivers) {}
