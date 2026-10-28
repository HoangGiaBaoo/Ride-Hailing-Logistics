package org.example.ride_hailing_logistics.driver.repository;

import org.example.ride_hailing_logistics.driver.entity.DriverProfile;
import org.example.ride_hailing_logistics.driver.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {

    Page<DriverProfile> findByReviewStatus(ReviewStatus status, Pageable pageable);
}
