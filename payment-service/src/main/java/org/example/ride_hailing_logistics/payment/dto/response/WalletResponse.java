package org.example.ride_hailing_logistics.payment.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(UUID walletId, UUID driverId, String currency, BigDecimal balance, String status) {}
