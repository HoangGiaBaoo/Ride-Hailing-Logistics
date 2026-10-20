package org.example.ride_hailing_logistics.trip;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.ride_hailing_logistics.common.domain.PaymentMethod;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.util.Normalizers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TripDtos {

    private TripDtos() {
    }

    public record RecipientRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 20) String phone) {}

    /** Khong co truong gia, trang thai hay customerId: client khong duoc tu quyet (NFR-SEC-012). */
    public record CreateTripRequest(
            @NotNull UUID quoteId,
            @NotNull PaymentMethod paymentMethod,
            boolean surgeConfirmed,
            @Valid RecipientRequest recipient,
            @Size(max = 500) String packageDescription,
            @Positive @DecimalMax("500") Double packageWeightKg) {}

    public record TransitionRequest(
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @Size(max = 10) String pin,
            @Size(max = 10) String deliveryCode) {}

    public record CancelRequest(@NotNull CancelReason reasonCode, @Size(max = 500) String note) {}

    public record PlaceView(double latitude, double longitude, String address) {}

    public record FareView(BigDecimal baseFare, BigDecimal distanceFare, BigDecimal timeFare, BigDecimal bookingFee,
                           BigDecimal surgeMultiplier, BigDecimal quotedTotal, BigDecimal finalFare,
                           BigDecimal cancellationFee, String currency, int pricingRuleVersion) {}

    public record DriverView(UUID driverId, String name, String maskedPhone, String vehiclePlate, String vehicleDescription) {}

    public record DeliveryView(String recipientName, String recipientPhone, String packageDescription, Double packageWeightKg) {}

    public record TripResponse(UUID id, String code, TripStatus status, String statusDisplay, ServiceType serviceType,
                               VehicleType vehicleType, PlaceView pickup, PlaceView dropoff, int distanceMeters,
                               int durationSeconds, FareView fare, PaymentMethod paymentMethod, DriverView driver,
                               DeliveryView delivery, String startPin, String deliveryCode, CancelReason cancelReason,
                               TripActorRole cancelledBy, Instant createdAt, Instant acceptedAt, Instant arrivedAt,
                               Instant startedAt, Instant completedAt, Instant endedAt, long version) {

        /**
         * Cat du lieu theo nguoi xem (BR-013, UI-DRV-005, DR-PRV-004):
         * - PIN va OTP chi khach thay (de doc cho tai xe / gui nguoi nhan)
         * - thong tin nguoi nhan chi tai xe DUOC GAN va khach thay
         * - SDT tai xe luon bi che (che so cuoc goi la ngoai pham vi)
         */
        static TripResponse of(Trip t, TripActorRole viewer) {
            boolean isCustomer = viewer == TripActorRole.CUSTOMER;
            boolean seesRecipient = isCustomer || viewer == TripActorRole.DRIVER || viewer == TripActorRole.SUPPORT_STAFF;
            DriverView driver = t.getDriverId() == null ? null : new DriverView(t.getDriverId(), t.getDriverName(),
                    Normalizers.maskPhone(t.getDriverPhone()), t.getVehiclePlate(), t.getVehicleDescription());
            DeliveryView delivery = t.getServiceType() != ServiceType.DELIVERY ? null : new DeliveryView(
                    seesRecipient ? t.getRecipientName() : null,
                    seesRecipient ? (viewer == TripActorRole.SUPPORT_STAFF ? Normalizers.maskPhone(t.getRecipientPhone()) : t.getRecipientPhone()) : null,
                    t.getPackageDescription(), t.getPackageWeightKg());
            return new TripResponse(t.getId(), t.getCode(), t.getStatus(), t.getStatus().getDisplayName(), t.getServiceType(),
                    t.getVehicleType(), new PlaceView(t.getPickupLat(), t.getPickupLng(), t.getPickupAddress()),
                    new PlaceView(t.getDropoffLat(), t.getDropoffLng(), t.getDropoffAddress()), t.getDistanceMeters(),
                    t.getDurationSeconds(), new FareView(t.getBaseFare(), t.getDistanceFare(), t.getTimeFare(),
                    t.getBookingFee(), t.getSurgeMultiplier(), t.getQuotedTotal(), t.getFinalFare(), t.getCancellationFee(),
                    t.getCurrency(), t.getPricingRuleVersion()),
                    t.getPaymentMethod(), driver, delivery,
                    isCustomer && !t.getStatus().isTerminal() ? t.getStartPin() : null,
                    isCustomer && !t.getStatus().isTerminal() ? t.getDeliveryCode() : null,
                    t.getCancelReasonCode(), t.getCancelledByRole(), t.getCreatedAt(), t.getAcceptedAt(), t.getArrivedAt(),
                    t.getStartedAt(), t.getCompletedAt(), t.getEndedAt(), t.getVersion());
        }
    }

    public record HistoryEntry(TripStatus from, TripStatus to, TripActorRole actorRole, String reason, Instant at) {
        static HistoryEntry from(TripStatusHistory h) {
            return new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getActorRole(), h.getReason(), h.getCreatedAt());
        }
    }

    public record CancellationPreview(boolean allowed, BigDecimal fee, String currency, String policyVersion, String message) {}
}
