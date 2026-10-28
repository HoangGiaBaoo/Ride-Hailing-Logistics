package org.example.ride_hailing_logistics.trip.client;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.client.RemoteFallbacks;
import org.example.ride_hailing_logistics.trip.client.dto.ConsumeQuoteRequest;
import org.example.ride_hailing_logistics.trip.client.dto.QuoteSnapshotResponse;
import org.example.ride_hailing_logistics.trip.client.dto.ReleaseQuoteRequest;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Khong co bao gia thi khong tao chuyen: tra loi ro rang thay vi doan gia (NFR-SEC-012). Loi 4xx giu nguyen ma. */
@Component
public class PricingClientFallbackFactory implements FallbackFactory<PricingClient> {

    @Override
    public PricingClient create(Throwable cause) {
        return new PricingClient() {
            @Override
            public ApiResponse<QuoteSnapshotResponse> consume(UUID quoteId, ConsumeQuoteRequest request) {
                throw RemoteFallbacks.translate("pricing-service", cause);
            }

            @Override
            public ApiResponse<Void> release(UUID quoteId, ReleaseQuoteRequest request) {
                throw RemoteFallbacks.translate("pricing-service", cause);
            }
        };
    }
}
