package org.example.ride_hailing_logistics.trip.dto.response;


public record DeliveryResponse(String recipientName, String recipientPhone, String packageDescription, Double packageWeightKg) {}
