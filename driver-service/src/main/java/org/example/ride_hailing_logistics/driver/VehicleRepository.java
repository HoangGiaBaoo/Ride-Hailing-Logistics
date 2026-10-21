package org.example.ride_hailing_logistics.driver;

import org.example.ride_hailing_logistics.driver.DriverEnums.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByDriverIdAndStatus(UUID driverId, VehicleStatus status);

    Optional<Vehicle> findByPlateNumber(String plateNumber);
}
