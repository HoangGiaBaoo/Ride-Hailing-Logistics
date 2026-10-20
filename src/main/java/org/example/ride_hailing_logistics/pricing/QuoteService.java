package org.example.ride_hailing_logistics.pricing;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.FareBreakdownResponse;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.Place;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteRequest;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteResponse;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.QuoteSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** UC-02 Tao bao gia. */
@Service
@RequiredArgsConstructor
public class QuoteService {

    private static final long MIN_DISTANCE_METERS = 100;

    private final PricingRuleRepository rules;
    private final FareQuoteRepository quotes;
    private final RouteEstimator routeEstimator;
    private final SurgeService surgeService;
    private final AppProperties props;
    private final Clock clock;

    @Transactional
    public QuoteResponse createQuote(UUID customerId, QuoteRequest req) {
        if (!req.vehicleType().supports(req.serviceType())) {
            throw BusinessException.rule("Loại xe " + req.vehicleType().getDisplayName() + " không phục vụ dịch vụ "
                    + req.serviceType().getDisplayName() + ".");
        }
        String region = regionOf(req.pickup())
                .filter(r -> regionOf(req.dropoff()).filter(r::equals).isPresent())
                .orElseThrow(() -> BusinessException.rule("Điểm đón hoặc điểm đến nằm ngoài vùng phục vụ."));
        RouteEstimator.RouteEstimate route = routeEstimator.estimate(req.pickup().latitude(), req.pickup().longitude(),
                req.dropoff().latitude(), req.dropoff().longitude());
        if (route.distanceMeters() < MIN_DISTANCE_METERS) {
            throw BusinessException.rule("Điểm đón và điểm đến quá gần nhau.");
        }
        Instant now = clock.instant();
        PricingRule rule = rules.findEffectiveAt(req.serviceType(), req.vehicleType(), region, now)
                .orElseThrow(() -> BusinessException.rule("Chưa có bảng giá cho dịch vụ này trong khu vực."));
        SurgeService.SurgeQuote surge = surgeService.current(surgeService.cellOf(req.pickup().latitude(), req.pickup().longitude()));
        FareCalculator.FareBreakdown fare = FareCalculator.calculate(rule.snapshot(), route.distanceMeters(),
                route.durationSeconds(), surge.multiplier(), props.pricing().roundingStep());
        FareQuote quote = quotes.save(FareQuote.issue(customerId, req.serviceType(), req.vehicleType(), region,
                req.pickup(), req.dropoff(), route, fare, surge.version(), surge.cell(), rule.getCurrency(), rule,
                now.plus(props.pricing().quoteTtl()), now));
        return toResponse(quote, fare);
    }

    /**
     * Mien trip goi trong transaction tao chuyen. Rollback tao chuyen thi quote van con ISSUED.
     * Quote cua nguoi khac / het han / da dung deu bi tu choi (FR-PRI-014, FR-PRI-015).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public QuoteSnapshot consumeQuote(UUID quoteId, UUID customerId) {
        Instant now = clock.instant();
        if (quotes.consume(quoteId, customerId, now) == 1) {
            return QuoteSnapshot.from(quotes.findById(quoteId).orElseThrow());
        }
        FareQuote quote = quotes.findById(quoteId)
                .filter(q -> q.getCustomerId().equals(customerId))
                .orElseThrow(() -> BusinessException.notFound("báo giá"));
        if (quote.getStatus() == FareQuote.Status.USED) {
            throw new BusinessException(ErrorCode.QUOTE_EXPIRED, "Báo giá này đã được dùng, vui lòng lấy báo giá mới.");
        }
        throw new BusinessException(ErrorCode.QUOTE_EXPIRED, "Báo giá đã hết hạn, vui lòng lấy báo giá mới.");
    }

    @Transactional(readOnly = true)
    public Optional<QuoteSnapshot> snapshot(UUID quoteId) {
        return quotes.findById(quoteId).map(QuoteSnapshot::from);
    }

    private Optional<String> regionOf(Place place) {
        return props.regions().stream().filter(r -> r.contains(place.latitude(), place.longitude()))
                .map(AppProperties.Region::code).findFirst();
    }

    private static QuoteResponse toResponse(FareQuote q, FareCalculator.FareBreakdown fare) {
        boolean surged = q.getSurgeMultiplier().compareTo(BigDecimal.ONE) > 0;
        return new QuoteResponse(q.getId(), q.getServiceType(), q.getVehicleType(),
                new Place(q.getPickupLat(), q.getPickupLng(), q.getPickupAddress()),
                new Place(q.getDropoffLat(), q.getDropoffLng(), q.getDropoffAddress()),
                q.getDistanceMeters(), q.getDurationSeconds(), q.isApproximate(),
                new FareBreakdownResponse(fare.baseFare(), fare.distanceFare(), fare.timeFare(), fare.bookingFee(),
                        fare.surgeAmount(), fare.minimumFare(), fare.minimumApplied()),
                q.getSurgeMultiplier(), surged, q.getTotal(), q.getCurrency(), q.getRuleVersion(), q.getSurgeVersion(),
                q.getExpiresAt());
    }
}
