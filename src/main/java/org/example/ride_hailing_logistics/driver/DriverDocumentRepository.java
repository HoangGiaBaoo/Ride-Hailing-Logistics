package org.example.ride_hailing_logistics.driver;

import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    List<DriverDocument> findByDriverIdAndStatusNot(UUID driverId, DocumentStatus status);
}
