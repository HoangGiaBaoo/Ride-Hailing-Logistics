package org.example.ride_hailing_logistics.pricing.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.pricing.client.route.RouteEstimator;
import org.example.ride_hailing_logistics.pricing.domain.FareCalculator;
import org.example.ride_hailing_logistics.pricing.dto.ActiveRule;
import org.example.ride_hailing_logistics.pricing.dto.Place;
import org.example.ride_hailing_logistics.pricing.dto.SurgeQuote;
import org.example.ride_hailing_logistics.pricing.dto.request.QuoteRequest;
import org.example.ride_hailing_logistics.pricing.dto.response.FareBreakdownResponse;
import org.example.ride_hailing_logistics.pricing.dto.response.QuoteResponse;
import org.example.ride_hailing_logistics.pricing.dto.response.QuoteSnapshotResponse;
import org.example.ride_hailing_logistics.pricing.entity.FareQuote;
import org.example.ride_hailing_logistics.pricing.event.FareFinalized;
import org.example.ride_hailing_logistics.pricing.event.PricingEvents;
import org.example.ride_hailing_logistics.pricing.exception.QuoteExpiredException;
import org.example.ride_hailing_logistics.pricing.exception.QuoteNotFoundException;
import org.example.ride_hailing_logistics.pricing.mapper.PricingMapper;
import org.example.ride_hailing_logistics.pricing.repository.FareQuoteRepository;
import org.example.ride_hailing_logistics.pricing.service.PricingRuleService;
import org.example.ride_hailing_logistics.pricing.service.QuoteService;
import org.example.ride_hailing_logistics.pricing.service.SurgeService;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteServiceImpl implements QuoteService {

    private static final long MIN_DISTANCE_METERS = 100;

    private final PricingRuleService ruleService;
    private final FareQuoteRepository quotes;
    private final RouteEstimator routeEstimator;
    private final SurgeService surgeService;
    private final PricingMapper mapper;
    private final OutboxService outbox;
    private final AppProperties props;
    private final Clock clock;

    @Override
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
        ActiveRule rule = ruleService.activeRules(req.serviceType(), req.vehicleType(), region).rules().stream()
                .filter(r -> r.isEffectiveAt(now))
                .findFirst()
                .orElseThrow(() -> BusinessException.rule("Chưa có bảng giá cho dịch vụ này trong khu vực."));
        SurgeQuote surge = surgeService.current(surgeService.cellOf(req.pickup().latitude(), req.pickup().longitude()));
        FareCalculator.FareBreakdown fare = FareCalculator.calculate(rule.snapshot(), route.distanceMeters(),
                route.durationSeconds(), surge.multiplier(), props.pricing().roundingStep());
        FareQuote quote = quotes.save(FareQuote.issue(customerId, req.serviceType(), req.vehicleType(), region,
                req.pickup(), req.dropoff(), route, fare, surge.version(), surge.cell(), rule.currency(), rule.id(),
                rule.ruleVersion(), now.plus(props.pricing().quoteTtl()), now));
        return toResponse(quote, fare);
    }

    /**
     * Goi tu trip-service (Feign) truoc khi tao chuyen. Idempotent theo tripId: goi lai sau khi da dung
     * (retry, mat phan hoi) van tra snapshot; quote cua nguoi khac / het han / da dung cho chuyen khac bi tu choi.
     */
    @Override
    @Transactional
    public QuoteSnapshotResponse consumeQuote(UUID quoteId, UUID customerId, UUID tripId) {
        Instant now = clock.instant();
        quotes.consume(quoteId, customerId, tripId, now);
        FareQuote quote = quotes.findById(quoteId)
                .filter(q -> q.getCustomerId().equals(customerId))
                .orElseThrow(QuoteNotFoundException::new);
        if (quote.getStatus() == FareQuote.Status.USED && tripId.equals(quote.getConsumedByTripId())) {
            return mapper.toSnapshot(quote);
        }
        if (quote.getStatus() == FareQuote.Status.USED) {
            throw new QuoteExpiredException("Báo giá này đã được dùng, vui lòng lấy báo giá mới.");
        }
        throw new QuoteExpiredException("Báo giá đã hết hạn, vui lòng lấy báo giá mới.");
    }

    @Override
    @Transactional
    public void releaseQuote(UUID quoteId, UUID tripId) {
        quotes.release(quoteId, tripId);
    }

    @Override
    @Transactional
    public void finalizeFare(UUID tripId, String tripCode, UUID customerId, UUID driverId, UUID quoteId,
                             String serviceType, String paymentMethod) {
        FareQuote q = quotes.findById(quoteId)
                .orElseThrow(() -> new IllegalStateException("Quote " + quoteId + " not found"));
        outbox.append(PricingEvents.PRODUCER, "Trip", tripId, PricingEvents.FARE_FINALIZED, 1, new FareFinalized(tripId,
                tripCode, customerId, driverId, serviceType, paymentMethod, q.getBaseFare(), q.getDistanceFare(),
                q.getTimeFare(), q.getBookingFee(), q.getSurgeMultiplier(), q.getTotal(), q.getCurrency(), q.getRuleId(),
                q.getRuleVersion(), "upfront-v1", clock.instant()));
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
