package org.example.ride_hailing_logistics.pricing.service;

import org.example.ride_hailing_logistics.pricing.dto.request.QuoteRequest;
import org.example.ride_hailing_logistics.pricing.dto.response.QuoteResponse;
import org.example.ride_hailing_logistics.pricing.dto.response.QuoteSnapshotResponse;

import java.util.UUID;

/** UC-02 Tao bao gia; dung quote khi tao chuyen; chot cuoc cuoi. */
public interface QuoteService {

    QuoteResponse createQuote(UUID customerId, QuoteRequest request);

    QuoteSnapshotResponse consumeQuote(UUID quoteId, UUID customerId, UUID tripId);

    void releaseQuote(UUID quoteId, UUID tripId);

    void finalizeFare(UUID tripId, String tripCode, UUID customerId, UUID driverId, UUID quoteId, String serviceType,
                      String paymentMethod);
}
