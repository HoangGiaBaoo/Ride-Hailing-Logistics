package org.example.ride_hailing_logistics.payment.dto.response;

import org.example.ride_hailing_logistics.payment.enums.EntryType;
import org.example.ride_hailing_logistics.payment.enums.ReferenceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletEntryResponse(UUID id, EntryType type, BigDecimal amount, String currency, ReferenceType referenceType,
                            String referenceId, String description, Instant createdAt) {}
