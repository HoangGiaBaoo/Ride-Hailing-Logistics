package org.example.ride_hailing_logistics.driver.repository;

import org.example.ride_hailing_logistics.driver.entity.DriverReviewDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DriverReviewDecisionRepository extends JpaRepository<DriverReviewDecision, UUID> {

    List<DriverReviewDecision> findByDriverIdOrderByCreatedAtAsc(UUID driverId);
}
