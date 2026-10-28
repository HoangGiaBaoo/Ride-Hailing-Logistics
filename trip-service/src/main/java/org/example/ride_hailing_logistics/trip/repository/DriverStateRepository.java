package org.example.ride_hailing_logistics.trip.repository;

import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.trip.dto.AvailabilityRow;
import org.example.ride_hailing_logistics.trip.entity.DriverState;
import org.example.ride_hailing_logistics.trip.enums.DriverAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Moi chuyen trang thai la UPDATE ... WHERE availability = :from, goi noi kiem so dong = 1 (CON-06, DR-009).
 * Khong bao gio doc len roi set: hai luong cung thay AVAILABLE se cung giu duoc mot tai xe.
 */
public interface DriverStateRepository extends JpaRepository<DriverState, UUID> {

    @Query("""
            select new org.example.ride_hailing_logistics.trip.dto.AvailabilityRow(d.availability, d.vehicleType, d.version)
            from DriverState d where d.driverId = :id
            """)
    Optional<AvailabilityRow> findAvailability(@Param("id") UUID driverId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update DriverState d set d.availability = :to, d.availabilityChangedAt = :now, d.version = d.version + 1
            where d.driverId = :id and d.availability = :from
            """)
    int transition(@Param("id") UUID driverId, @Param("from") DriverAvailability from,
                   @Param("to") DriverAvailability to, @Param("now") Instant now);

    /** OFFLINE -> AVAILABLE chi khi khong bi dinh chi / khoa tai khoan (co the vua den qua event). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update DriverState d set d.availability = 'AVAILABLE', d.availabilityChangedAt = :now, d.version = d.version + 1
            where d.driverId = :id and d.availability = 'OFFLINE' and d.suspended = false and d.accountDisabled = false
            """)
    int goOnline(@Param("id") UUID driverId, @Param("now") Instant now);

    /** Tra tai xe ve AVAILABLE; neu bi dinh chi/khoa trong luc do thi ve OFFLINE (FR-DRV-013). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE driver_states
               SET availability = CASE WHEN suspended OR account_disabled THEN 'OFFLINE' ELSE 'AVAILABLE' END,
                   availability_changed_at = :now, version = version + 1
             WHERE driver_id = :id AND availability = :from
            """, nativeQuery = true)
    int releaseFrom(@Param("id") UUID driverId, @Param("from") String from, @Param("now") Instant now);

    /** Ban chup tai xe + xe lay tu driver-service luc bam Online; khong dung toi co dinh chi/khoa. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO driver_states (driver_id, availability, suspended, account_disabled, full_name, phone, vehicle_id,
                                       vehicle_type, plate_number, vehicle_description, availability_changed_at, version)
            VALUES (:id, 'OFFLINE', false, false, :fullName, :phone, :vehicleId, :vehicleType, :plateNumber,
                    :vehicleDescription, :now, 0)
            ON CONFLICT (driver_id) DO UPDATE
               SET full_name = EXCLUDED.full_name, phone = EXCLUDED.phone, vehicle_id = EXCLUDED.vehicle_id,
                   vehicle_type = EXCLUDED.vehicle_type, plate_number = EXCLUDED.plate_number,
                   vehicle_description = EXCLUDED.vehicle_description
            """, nativeQuery = true)
    int upsertSnapshot(@Param("id") UUID driverId, @Param("fullName") String fullName, @Param("phone") String phone,
                       @Param("vehicleId") UUID vehicleId, @Param("vehicleType") String vehicleType,
                       @Param("plateNumber") String plateNumber, @Param("vehicleDescription") String vehicleDescription,
                       @Param("now") Instant now);

    /** Upsert: event dinh chi co the den truoc khi tai xe tung Online — van phai ghi nhan de chan lan Online dau. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO driver_states (driver_id, availability, suspended, account_disabled, availability_changed_at, version)
            VALUES (:id, 'OFFLINE', :suspended, false, :now, 0)
            ON CONFLICT (driver_id) DO UPDATE SET suspended = EXCLUDED.suspended
            """, nativeQuery = true)
    int setSuspended(@Param("id") UUID driverId, @Param("suspended") boolean suspended, @Param("now") Instant now);

    /** Chi cap nhat dong da co: UserStatusChanged phat cho moi nguoi dung, khong tao dong cho khach hang. */
    @Modifying(flushAutomatically = true)
    @Query("update DriverState d set d.accountDisabled = :disabled where d.driverId = :id")
    int setAccountDisabled(@Param("id") UUID driverId, @Param("disabled") boolean disabled);

    @Modifying(flushAutomatically = true)
    @Query("update DriverState d set d.fullName = :fullName where d.driverId = :id")
    int setFullName(@Param("id") UUID driverId, @Param("fullName") String fullName);

    /** Loc ung vien du dieu kien nhan offer (FR-MAT-002). */
    @Query("""
            select d.driverId from DriverState d
            where d.driverId in :ids and d.availability = 'AVAILABLE' and d.vehicleType = :vehicleType
              and d.suspended = false and d.accountDisabled = false
            """)
    List<UUID> findEligible(@Param("ids") Collection<UUID> driverIds, @Param("vehicleType") VehicleType vehicleType);
}
