package org.example.ride_hailing_logistics.driver.repository;

import org.example.ride_hailing_logistics.driver.entity.Vehicle;
import org.example.ride_hailing_logistics.driver.enums.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByDriverIdAndStatus(UUID driverId, VehicleStatus status);

    Optional<Vehicle> findByPlateNumber(String plateNumber);
}
