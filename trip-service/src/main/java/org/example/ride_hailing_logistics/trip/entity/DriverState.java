package org.example.ride_hailing_logistics.trip.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.trip.enums.DriverAvailability;

import java.time.Instant;
import java.util.UUID;

/**
 * Trang thai kha dung + ban chup tai xe/xe, dat cung DB voi trips.
 * Ly do: chap nhan offer phai doi trip, offer va tai xe trong MOT transaction (FR-MAT-010) — tach sang
 * driver-service se thanh giao dich phan tan. Ho so goc van o driver-service; o day chi la ban sao de ghep chuyen.
 * Moi lan ghi di qua cau lenh co dieu kien trong DriverStateRepository, entity chi de doc.
 */
@Entity
@Table(name = "driver_states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverState {

    @Id
    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    private DriverAvailability availability;

    private boolean suspended;

    private boolean accountDisabled;

    private String fullName;

    private String phone;

    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    private VehicleType vehicleType;

    private String plateNumber;

    private String vehicleDescription;

    private Instant availabilityChangedAt;

    @Version
    private long version;
}
