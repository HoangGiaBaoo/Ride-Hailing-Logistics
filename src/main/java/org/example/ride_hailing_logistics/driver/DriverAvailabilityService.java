package org.example.ride_hailing_logistics.driver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.driver.DriverDtos.AvailabilityResponse;
import org.example.ride_hailing_logistics.driver.DriverDtos.DriverSnapshot;
import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentStatus;
import org.example.ride_hailing_logistics.driver.DriverEnums.DriverAvailability;
import org.example.ride_hailing_logistics.driver.DriverEnums.VehicleStatus;
import org.example.ride_hailing_logistics.identity.UserDirectory;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API cong khai ve trang thai kha dung cua tai xe, dung boi chinh tai xe (Online/Offline)
 * va boi mien trip/dispatch (giu, tra, gan). Moi chuyen trang thai la cap nhat co dieu kien.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverAvailabilityService {

    public static final String EVENT_AVAILABILITY_CHANGED = "DriverAvailabilityChanged";

    private final DriverProfileRepository profiles;
    private final VehicleRepository vehicles;
    private final DriverDocumentRepository documents;
    private final UserDirectory userDirectory;
    private final OutboxService outbox;
    private final Clock clock;

    @Transactional
    public AvailabilityResponse goOnline(UUID driverId) {
        DriverProfile profile = load(driverId);
        if (profile.getAvailability() != DriverAvailability.OFFLINE) {
            return new AvailabilityResponse(profile.getAvailability(), List.of());   // da online: idempotent
        }
        List<String> blocking = blockingReasons(profile);
        if (!blocking.isEmpty()) {
            // Giai thich ly do khong duoc Online (UI-DRV-002)
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Bạn chưa đủ điều kiện hoạt động.",
                    Map.of("reasons", blocking));
        }
        if (profiles.transition(driverId, DriverAvailability.OFFLINE, DriverAvailability.AVAILABLE, clock.instant()) == 1) {
            publishChanged(driverId, DriverAvailability.OFFLINE, DriverAvailability.AVAILABLE);
            return new AvailabilityResponse(DriverAvailability.AVAILABLE, List.of());
        }
        return new AvailabilityResponse(currentAvailability(driverId), List.of());
    }

    @Transactional
    public AvailabilityResponse goOffline(UUID driverId) {
        DriverProfile profile = load(driverId);
        switch (profile.getAvailability()) {
            case OFFLINE -> {
                return new AvailabilityResponse(DriverAvailability.OFFLINE, List.of());
            }
            case OFFERED, BUSY -> throw BusinessException.rule(
                    "Không thể chuyển Offline khi đang có đề nghị hoặc chuyến đang thực hiện.");   // FR-DRV-011
            case AVAILABLE -> {
                if (profiles.transition(driverId, DriverAvailability.AVAILABLE, DriverAvailability.OFFLINE, clock.instant()) != 1) {
                    // Vua bi giu cho mot offer giua luc doc va ghi
                    throw BusinessException.rule("Bạn vừa nhận được đề nghị chuyến, vui lòng xử lý đề nghị trước.");
                }
                publishChanged(driverId, DriverAvailability.AVAILABLE, DriverAvailability.OFFLINE);
            }
        }
        return new AvailabilityResponse(DriverAvailability.OFFLINE, List.of());
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse status(UUID driverId) {
        DriverProfile profile = load(driverId);
        return new AvailabilityResponse(profile.getAvailability(), blockingReasons(profile));
    }

    // ---------- dung boi dispatch ----------

    @Transactional(readOnly = true)
    public List<EligibleDriverRow> filterEligible(Collection<UUID> driverIds, VehicleType vehicleType) {
        return driverIds.isEmpty() ? List.of() : profiles.findEligible(driverIds, vehicleType);
    }

    /** AVAILABLE -> OFFERED. false = tai xe vua bi giu cho chuyen khac hoac vua Offline. */
    @Transactional
    public boolean reserve(UUID driverId) {
        return profiles.transition(driverId, DriverAvailability.AVAILABLE, DriverAvailability.OFFERED, clock.instant()) == 1;
    }

    /** OFFERED -> AVAILABLE (hoac OFFLINE neu bi dinh chi trong luc do). Goi lap lai cung an toan. */
    @Transactional
    public boolean releaseOffer(UUID driverId) {
        return profiles.releaseFrom(driverId, DriverAvailability.OFFERED.name(), clock.instant()) == 1;
    }

    /** OFFERED -> BUSY khi chap nhan offer thanh cong. */
    @Transactional
    public boolean markBusy(UUID driverId) {
        return profiles.transition(driverId, DriverAvailability.OFFERED, DriverAvailability.BUSY, clock.instant()) == 1;
    }

    /** BUSY -> AVAILABLE/OFFLINE khi chuyen ket thuc. */
    @Transactional
    public boolean finishTrip(UUID driverId) {
        return profiles.releaseFrom(driverId, DriverAvailability.BUSY.name(), clock.instant()) == 1;
    }

    @Transactional(readOnly = true)
    public DriverAvailability availabilityOf(UUID driverId) {
        return currentAvailability(driverId);
    }

    private DriverAvailability currentAvailability(UUID driverId) {
        return profiles.currentAvailability(driverId).orElseThrow(() -> BusinessException.notFound("hồ sơ tài xế"));
    }

    @Transactional(readOnly = true)
    public DriverSnapshot snapshot(UUID driverId) {
        Vehicle vehicle = vehicles.findByDriverIdAndStatus(driverId, VehicleStatus.ACTIVE)
                .orElseThrow(() -> BusinessException.notFound("phương tiện của tài xế"));
        UserDirectory.UserBasic user = userDirectory.find(driverId).orElseThrow(() -> BusinessException.notFound("tài xế"));
        return new DriverSnapshot(driverId, user.fullName(), user.phone(), vehicle.getId(), vehicle.getVehicleType(),
                vehicle.getPlateNumber(), vehicle.description());
    }

    @Transactional(readOnly = true)
    public long countAvailable() {
        return profiles.countByAvailability(DriverAvailability.AVAILABLE);
    }

    /** Nguon cung cho surge: so tai xe AVAILABLE trong tap tai xe co vi tri moi (FR-PRI-008). */
    @Transactional(readOnly = true)
    public long countAvailableAmong(Collection<UUID> driverIds) {
        return driverIds.isEmpty() ? 0 : profiles.countByIdInAndAvailability(driverIds, DriverAvailability.AVAILABLE);
    }

    /** Ngu canh de mien location nhan telemetry: tai xe co dang hoat dong khong, chay loai xe nao. */
    @Transactional(readOnly = true)
    public Optional<TelemetryContext> telemetryContext(UUID driverId) {
        return profiles.findById(driverId).flatMap(p -> vehicles.findByDriverIdAndStatus(driverId, VehicleStatus.ACTIVE)
                .map(v -> new TelemetryContext(p.getAvailability(), v.getVehicleType())));
    }

    public record TelemetryContext(DriverAvailability availability, VehicleType vehicleType) {}

    /** Dinh chi: dua ve OFFLINE ngay neu dang ranh; neu dang offer/chuyen thi se ve OFFLINE khi ket thuc. */
    @Transactional
    void forceOfflineIfIdle(UUID driverId) {
        if (profiles.transition(driverId, DriverAvailability.AVAILABLE, DriverAvailability.OFFLINE, clock.instant()) == 1) {
            publishChanged(driverId, DriverAvailability.AVAILABLE, DriverAvailability.OFFLINE);
        }
    }

    private List<String> blockingReasons(DriverProfile profile) {
        UUID id = profile.getId();
        boolean active = userDirectory.find(id).map(UserDirectory.UserBasic::active).orElse(false);
        Vehicle vehicle = vehicles.findByDriverIdAndStatus(id, VehicleStatus.ACTIVE).orElse(null);
        return DriverEligibility.blockingOnline(active, profile, vehicle,
                documents.findByDriverIdAndStatusNot(id, DocumentStatus.SUPERSEDED),
                LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private void publishChanged(UUID driverId, DriverAvailability from, DriverAvailability to) {
        Instant now = clock.instant();
        outbox.append("driver", "Driver", driverId, EVENT_AVAILABILITY_CHANGED, 1,
                new AvailabilityChangedPayload(driverId, from, to, now));
    }

    private DriverProfile load(UUID driverId) {
        return profiles.findById(driverId).orElseThrow(() -> BusinessException.notFound("hồ sơ tài xế"));
    }

    public record AvailabilityChangedPayload(UUID driverId, DriverAvailability oldStatus, DriverAvailability newStatus,
                                             Instant occurredAt) {}
}
