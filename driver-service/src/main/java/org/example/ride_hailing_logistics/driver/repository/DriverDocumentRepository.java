package org.example.ride_hailing_logistics.driver.repository;

import org.example.ride_hailing_logistics.driver.entity.DriverDocument;
import org.example.ride_hailing_logistics.driver.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    List<DriverDocument> findByDriverIdAndStatusNot(UUID driverId, DocumentStatus status);
}
