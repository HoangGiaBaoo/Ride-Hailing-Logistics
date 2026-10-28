package org.example.ride_hailing_logistics.pricing.dto;

import java.math.BigDecimal;

public record SurgeQuote(BigDecimal multiplier, String version, String cell, long demand, long supply) {}
