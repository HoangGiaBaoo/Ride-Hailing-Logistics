package org.example.ride_hailing_logistics.driver.entity;

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
import org.example.ride_hailing_logistics.driver.enums.VehicleStatus;
import java.time.Instant;
import java.util.UUID;

/** FR-DRV-007: loai, bien so, thuoc tinh nhan dien, trang thai, lien ket tai xe. */
@Entity
@Table(name = "vehicles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vehicle {

    @Id
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "plate_number", nullable = false, length = 15)
    private String plateNumber;

    @Column(nullable = false, length = 50)
    private String brand;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false, length = 30)
    private String color;

    @Column(name = "manufacture_year", nullable = false)
    private int manufactureYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Vehicle create(UUID driverId, VehicleType type, String plate, String brand, String model, String color,
                          int year, Instant now) {
        Vehicle v = new Vehicle();
        v.id = UUID.randomUUID();
        v.driverId = driverId;
        v.status = VehicleStatus.ACTIVE;
        v.createdAt = now;
        v.update(type, plate, brand, model, color, year, now);
        return v;
    }

    public void update(VehicleType type, String plate, String brand, String model, String color, int year, Instant now) {
        this.vehicleType = type;
        this.plateNumber = plate;
        this.brand = brand;
        this.model = model;
        this.color = color;
        this.manufactureYear = year;
        this.updatedAt = now;
    }

    public String description() {
        return brand + " " + model + " - " + color;
    }
}
