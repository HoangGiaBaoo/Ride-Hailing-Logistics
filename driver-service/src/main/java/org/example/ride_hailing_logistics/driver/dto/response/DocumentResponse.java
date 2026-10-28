package org.example.ride_hailing_logistics.driver.dto.response;

import org.example.ride_hailing_logistics.driver.enums.DocumentStatus;
import org.example.ride_hailing_logistics.driver.enums.DocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** So giay to chi tra ve dang che (NFR-PRV-002). */
public record DocumentResponse(UUID id, DocumentType type, String maskedNumber, LocalDate expiresAt,
                               DocumentStatus status, String contentType, long fileSize, Instant uploadedAt) {}
