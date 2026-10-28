package org.example.ride_hailing_logistics.trip.repository;

import org.example.ride_hailing_logistics.trip.entity.TripStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TripStatusHistoryRepository extends JpaRepository<TripStatusHistory, Long> {

    List<TripStatusHistory> findByTripIdOrderByIdAsc(UUID tripId);
}
