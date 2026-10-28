package org.example.ride_hailing_logistics.pricing.dto;

import java.util.List;

/** Gia tri cache cho mot pham vi (dich vu, loai xe, vung): boc List de serializer JSON biet kieu phan tu. */
public record ActiveRules(List<ActiveRule> rules) {}
