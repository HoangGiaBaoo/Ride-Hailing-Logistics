package org.example.ride_hailing_logistics;

import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.trip.client.PricingClient;
import org.example.ride_hailing_logistics.trip.client.QuoteSnapshot;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ban gia pricing-service: quote dung mot lan theo tripId, cung ngu nghia voi QuoteService.consumeQuote
 * (ban that duoc kiem o QuoteConsumptionIT cua pricing-service).
 */
public class InMemoryPricing implements PricingClient {

    private record Entry(QuoteSnapshot quote, UUID usedBy) {}

    private final Map<UUID, Entry> quotes = new ConcurrentHashMap<>();

    public void reset() {
        quotes.clear();
    }

    /** Bao gia xe may 40.000 VND, khong surge: 12.000 + 4,2 km x 5.000 + 14 phut x 500 = 40.000. */
    public UUID issue(UUID customerId, double pickupLat, double pickupLng, double dropoffLat, double dropoffLng) {
        return issue(customerId, pickupLat, pickupLng, dropoffLat, dropoffLng, new BigDecimal("1.0"));
    }

    public UUID issue(UUID customerId, double pickupLat, double pickupLng, double dropoffLat, double dropoffLng,
                      BigDecimal surge) {
        UUID id = UUID.randomUUID();
        quotes.put(id, new Entry(new QuoteSnapshot(id, customerId, ServiceType.RIDE, VehicleType.MOTORBIKE, pickupLat, pickupLng,
                "Điểm đón", dropoffLat, dropoffLng, "Điểm đến", 4_200, 840, new BigDecimal("12000"), new BigDecimal("21000"),
                new BigDecimal("7000"), BigDecimal.ZERO, surge, new BigDecimal("40000"), "VND",
                UUID.randomUUID(), 1), null));
        return id;
    }

    @Override
    public ApiResponse<QuoteSnapshot> consume(UUID quoteId, ConsumeCommand cmd) {
        Entry entry = quotes.get(quoteId);
        if (entry == null || !entry.quote().customerId().equals(cmd.customerId())) {
            throw BusinessException.notFound("báo giá");
        }
        Entry after = quotes.compute(quoteId, (id, e) -> e.usedBy() == null ? new Entry(e.quote(), cmd.tripId()) : e);
        if (!cmd.tripId().equals(after.usedBy())) {
            throw new BusinessException(ErrorCode.QUOTE_EXPIRED, "Báo giá này đã được dùng, vui lòng lấy báo giá mới.");
        }
        return ApiResponse.ok(after.quote());
    }

    @Override
    public ApiResponse<Object> release(UUID quoteId, ReleaseCommand cmd) {
        quotes.computeIfPresent(quoteId, (id, e) -> cmd.tripId().equals(e.usedBy()) ? new Entry(e.quote(), null) : e);
        return ApiResponse.ok(null);
    }

    public boolean isUsed(UUID quoteId) {
        return quotes.get(quoteId).usedBy() != null;
    }
}
