package org.example.ride_hailing_logistics.trip.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.example.ride_hailing_logistics.trip.client.DriverClient;
import org.example.ride_hailing_logistics.trip.client.dto.DriverSnapshotResponse;
import org.example.ride_hailing_logistics.trip.client.dto.OnlineEligibilityResponse;
import org.example.ride_hailing_logistics.trip.dto.AvailabilityRow;
import org.example.ride_hailing_logistics.trip.dto.response.AvailabilityResponse;
import org.example.ride_hailing_logistics.trip.entity.DriverState;
import org.example.ride_hailing_logistics.trip.enums.DriverAvailability;
import org.example.ride_hailing_logistics.trip.event.DriverAvailabilityChanged;
import org.example.ride_hailing_logistics.trip.event.TripEvents;
import org.example.ride_hailing_logistics.trip.exception.DriverNotEligibleException;
import org.example.ride_hailing_logistics.trip.repository.DriverStateRepository;
import org.example.ride_hailing_logistics.trip.service.DriverStateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverStateServiceImpl implements DriverStateService {

    private final DriverStateRepository states;
    private final DriverClient driverClient;
    private final OutboxService outbox;
    private final TransactionTemplate tx;
    private final Clock clock;

    /**
     * Hoi driver-service (giay to, xe, ho so) NGOAI transaction, roi ghi ban chup + OFFLINE -> AVAILABLE co dieu kien.
     * Neu event dinh chi den giua hai buoc, cau UPDATE kiem suspended = false se chan lai.
     */
    @Override
    public AvailabilityResponse goOnline(UUID driverId) {
        Optional<AvailabilityRow> current = states.findAvailability(driverId);
        if (current.isPresent() && current.get().availability() != DriverAvailability.OFFLINE) {
            return new AvailabilityResponse(current.get().availability(), List.of());   // da online: idempotent
        }
        OnlineEligibilityResponse eligibility = driverClient.onlineEligibility(driverId).data();
        if (!eligibility.eligible()) {
            throw new DriverNotEligibleException(eligibility.reasons());
        }
        DriverSnapshotResponse s = eligibility.snapshot();
        return tx.execute(status -> {
            Instant now = clock.instant();
            states.upsertSnapshot(driverId, s.fullName(), s.phone(), s.vehicleId(), s.vehicleType().name(),
                    s.plateNumber(), s.vehicleDescription(), now);
            if (states.goOnline(driverId, now) == 1) {
                publishChanged(driverId, DriverAvailability.OFFLINE);
                return new AvailabilityResponse(DriverAvailability.AVAILABLE, List.of());
            }
            AvailabilityRow row = states.findAvailability(driverId).orElseThrow();
            if (row.availability() == DriverAvailability.OFFLINE) {
                // Co dinh chi/khoa vua den qua event sau khi driver-service tra loi
                throw new DriverNotEligibleException(List.of("Tài khoản tài xế đang bị tạm khóa."));
            }
            return new AvailabilityResponse(row.availability(), List.of());
        });
    }

    @Override
    @Transactional
    public AvailabilityResponse goOffline(UUID driverId) {
        AvailabilityRow row = states.findAvailability(driverId).orElse(null);
        if (row == null || row.availability() == DriverAvailability.OFFLINE) {
            return new AvailabilityResponse(DriverAvailability.OFFLINE, List.of());
        }
        if (row.availability() != DriverAvailability.AVAILABLE) {
            // FR-DRV-011
            throw BusinessException.rule("Không thể chuyển Offline khi đang có đề nghị hoặc chuyến đang thực hiện.");
        }
        if (!transition(driverId, DriverAvailability.AVAILABLE, DriverAvailability.OFFLINE)) {
            // Vua bi giu cho mot offer giua luc doc va ghi
            throw BusinessException.rule("Bạn vừa nhận được đề nghị chuyến, vui lòng xử lý đề nghị trước.");
        }
        return new AvailabilityResponse(DriverAvailability.OFFLINE, List.of());
    }

    @Override
    public AvailabilityResponse status(UUID driverId) {
        DriverAvailability availability = states.findAvailability(driverId).map(AvailabilityRow::availability)
                .orElse(DriverAvailability.OFFLINE);
        if (availability != DriverAvailability.OFFLINE) {
            return new AvailabilityResponse(availability, List.of());
        }
        try {
            OnlineEligibilityResponse eligibility = driverClient.onlineEligibility(driverId).data();
            return new AvailabilityResponse(availability, eligibility.reasons());
        } catch (BusinessException e) {
            if (e.getCode() != ErrorCode.DEPENDENCY_UNAVAILABLE) throw e;
            // Khong hoi duoc ly do: van tra trang thai, nut Online se hoi lai
            return new AvailabilityResponse(availability, List.of());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> filterEligible(Collection<UUID> driverIds, VehicleType vehicleType) {
        return driverIds.isEmpty() ? Set.of() : new HashSet<>(states.findEligible(driverIds, vehicleType));
    }

    @Override
    @Transactional
    public boolean reserve(UUID driverId) {
        return transition(driverId, DriverAvailability.AVAILABLE, DriverAvailability.OFFERED);
    }

    @Override
    @Transactional
    public boolean releaseOffer(UUID driverId) {
        return release(driverId, DriverAvailability.OFFERED);
    }

    @Override
    @Transactional
    public boolean markBusy(UUID driverId) {
        return transition(driverId, DriverAvailability.OFFERED, DriverAvailability.BUSY);
    }

    @Override
    @Transactional
    public boolean finishTrip(UUID driverId) {
        return release(driverId, DriverAvailability.BUSY);
    }

    @Override
    @Transactional(readOnly = true)
    public DriverState require(UUID driverId) {
        return states.findById(driverId).filter(s -> s.getFullName() != null)
                .orElseThrow(() -> BusinessException.notFound("tài xế"));
    }

    /** Dinh chi: dang ranh thi Offline ngay; dang offer/chuyen thi releaseFrom se dua ve OFFLINE khi ket thuc. */
    @Override
    @Transactional
    public void applySuspension(UUID driverId, boolean suspended) {
        states.setSuspended(driverId, suspended, clock.instant());
        if (suspended) {
            transition(driverId, DriverAvailability.AVAILABLE, DriverAvailability.OFFLINE);
        }
    }

    @Override
    @Transactional
    public void applyAccountStatus(UUID driverId, boolean disabled) {
        if (states.setAccountDisabled(driverId, disabled) == 1 && disabled) {
            transition(driverId, DriverAvailability.AVAILABLE, DriverAvailability.OFFLINE);
        }
    }

    @Override
    @Transactional
    public void applyProfileName(UUID driverId, String fullName) {
        states.setFullName(driverId, fullName);
    }

    private boolean transition(UUID driverId, DriverAvailability from, DriverAvailability to) {
        if (states.transition(driverId, from, to, clock.instant()) != 1) return false;
        publishChanged(driverId, from);
        return true;
    }

    private boolean release(UUID driverId, DriverAvailability from) {
        if (states.releaseFrom(driverId, from.name(), clock.instant()) != 1) return false;
        publishChanged(driverId, from);
        return true;
    }

    /** Doc lai sau cap nhat co dieu kien (cung transaction) de event mang dung trang thai moi + version. */
    private void publishChanged(UUID driverId, DriverAvailability from) {
        AvailabilityRow row = states.findAvailability(driverId).orElseThrow();
        outbox.append(TripEvents.PRODUCER, "Driver", driverId, TripEvents.DRIVER_AVAILABILITY_CHANGED, 1,
                new DriverAvailabilityChanged(driverId, from, row.availability(), row.vehicleType(), row.version(),
                        clock.instant()));
    }
}
