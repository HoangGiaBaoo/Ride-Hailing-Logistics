package org.example.ride_hailing_logistics.trip.mapper;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.util.Normalizers;
import org.example.ride_hailing_logistics.trip.dto.response.DeliveryResponse;
import org.example.ride_hailing_logistics.trip.dto.response.DriverInfoResponse;
import org.example.ride_hailing_logistics.trip.dto.response.FareResponse;
import org.example.ride_hailing_logistics.trip.dto.response.HistoryEntryResponse;
import org.example.ride_hailing_logistics.trip.dto.response.OfferResponse;
import org.example.ride_hailing_logistics.trip.dto.response.PlaceResponse;
import org.example.ride_hailing_logistics.trip.dto.response.TripResponse;
import org.example.ride_hailing_logistics.trip.entity.DriverOffer;
import org.example.ride_hailing_logistics.trip.entity.Trip;
import org.example.ride_hailing_logistics.trip.entity.TripStatusHistory;
import org.example.ride_hailing_logistics.trip.enums.TripActorRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity -> DTO. Phan an/hien PII theo nguoi xem viet tay (default method) vi do la quy tac bao mat,
 * khong phai anh xa truong: PIN/ma giao chi cho khach khi chuyen chua xong; SDT nguoi nhan chi cho
 * khach/tai xe duoc gan, nhan vien thay ban che (BR-013, NFR-PRV-002).
 */
@Mapper
public interface TripMapper {

    @Mapping(target = "from", source = "fromStatus")
    @Mapping(target = "to", source = "toStatus")
    @Mapping(target = "at", source = "createdAt")
    HistoryEntryResponse toHistoryEntry(TripStatusHistory history);

    @Mapping(target = "offerId", source = "offer.id")
    @Mapping(target = "tripId", source = "trip.id")
    @Mapping(target = "serviceType", source = "trip.serviceType")
    @Mapping(target = "pickupLat", source = "trip.pickupLat")
    @Mapping(target = "pickupLng", source = "trip.pickupLng")
    @Mapping(target = "pickupAddress", source = "trip.pickupAddress")
    @Mapping(target = "dropoffAddress", source = "trip.dropoffAddress")
    @Mapping(target = "estimatedPickupDistanceMeters", source = "offer.distanceMeters")
    @Mapping(target = "estimatedFare", source = "trip.quotedTotal")
    @Mapping(target = "currency", source = "trip.currency")
    @Mapping(target = "expiresAt", source = "offer.expiresAt")
    OfferResponse toOffer(DriverOffer offer, Trip trip);

    default TripResponse toResponse(Trip t, TripActorRole viewer) {
        boolean isCustomer = viewer == TripActorRole.CUSTOMER;
        boolean seesRecipient = isCustomer || viewer == TripActorRole.DRIVER || viewer == TripActorRole.SUPPORT_STAFF;
        DriverInfoResponse driver = t.getDriverId() == null ? null : new DriverInfoResponse(t.getDriverId(), t.getDriverName(),
                Normalizers.maskPhone(t.getDriverPhone()), t.getVehiclePlate(), t.getVehicleDescription());
        DeliveryResponse delivery = t.getServiceType() != ServiceType.DELIVERY ? null : new DeliveryResponse(
                seesRecipient ? t.getRecipientName() : null,
                seesRecipient ? (viewer == TripActorRole.SUPPORT_STAFF ? Normalizers.maskPhone(t.getRecipientPhone()) : t.getRecipientPhone()) : null,
                t.getPackageDescription(), t.getPackageWeightKg());
        boolean showSecrets = isCustomer && !t.getStatus().isTerminal();
        return new TripResponse(t.getId(), t.getCode(), t.getStatus(), t.getStatus().getDisplayName(), t.getServiceType(),
                t.getVehicleType(), new PlaceResponse(t.getPickupLat(), t.getPickupLng(), t.getPickupAddress()),
                new PlaceResponse(t.getDropoffLat(), t.getDropoffLng(), t.getDropoffAddress()), t.getDistanceMeters(),
                t.getDurationSeconds(), new FareResponse(t.getBaseFare(), t.getDistanceFare(), t.getTimeFare(),
                t.getBookingFee(), t.getSurgeMultiplier(), t.getQuotedTotal(), t.getFinalFare(), t.getCancellationFee(),
                t.getCurrency(), t.getPricingRuleVersion()),
                t.getPaymentMethod(), driver, delivery,
                showSecrets ? t.getStartPin() : null,
                showSecrets ? t.getDeliveryCode() : null,
                t.getCancelReasonCode(), t.getCancelledByRole(), t.getCreatedAt(), t.getAcceptedAt(), t.getArrivedAt(),
                t.getStartedAt(), t.getCompletedAt(), t.getEndedAt(), t.getVersion());
    }
}
