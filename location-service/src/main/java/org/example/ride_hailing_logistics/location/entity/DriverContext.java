package org.example.ride_hailing_logistics.location.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.location.enums.DriverAvailability;

import java.time.Instant;
import java.util.UUID;

/**
 * Ngu canh tai xe de nhan telemetry: dang hoat dong hay khong, chay loai xe nao.
 * Cap nhat tu event cua trip-service; cot source_version la version cua driver_states ben trip,
 * chi ghi de khi ban tin moi hon (Kafka giao lai / lech thu tu khong lam lui trang thai).
 */
@Entity
@Table(name = "driver_contexts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverContext {

    @Id
    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DriverAvailability availability;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isActive() {
        return availability != DriverAvailability.OFFLINE;
    }
}
