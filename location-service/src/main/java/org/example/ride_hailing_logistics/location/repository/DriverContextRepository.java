package org.example.ride_hailing_logistics.location.repository;

import org.example.ride_hailing_logistics.location.entity.DriverContext;
import org.example.ride_hailing_logistics.location.enums.DriverAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface DriverContextRepository extends JpaRepository<DriverContext, UUID> {

    /** Upsert co dieu kien version: ban tin cu hon ban da luu khong ghi de (tra 0 dong). */
    @Modifying
    @Query(value = """
            INSERT INTO driver_contexts (driver_id, availability, vehicle_type, source_version, updated_at)
            VALUES (:driverId, :availability, :vehicleType, :version, :now)
            ON CONFLICT (driver_id) DO UPDATE
               SET availability = EXCLUDED.availability, vehicle_type = EXCLUDED.vehicle_type,
                   source_version = EXCLUDED.source_version, updated_at = EXCLUDED.updated_at
             WHERE driver_contexts.source_version < EXCLUDED.source_version
            """, nativeQuery = true)
    int upsertIfNewer(@Param("driverId") UUID driverId, @Param("availability") String availability,
                      @Param("vehicleType") String vehicleType, @Param("version") long version,
                      @Param("now") Instant now);

    long countByDriverIdInAndAvailability(Collection<UUID> driverIds, DriverAvailability availability);
}
