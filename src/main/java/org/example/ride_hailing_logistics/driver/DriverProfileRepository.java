package org.example.ride_hailing_logistics.driver;

import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.driver.DriverEnums.DriverAvailability;
import org.example.ride_hailing_logistics.driver.DriverEnums.ReviewStatus;
import org.example.ride_hailing_logistics.driver.DriverEnums.VehicleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {

    Page<DriverProfile> findByReviewStatus(ReviewStatus status, Pageable pageable);

    /**
     * Chuyen trang thai kha dung CO DIEU KIEN: chi doi khi dang o trang thai mong doi.
     * Tra ve so dong cap nhat; 0 nghia la luong khac da doi truoc -> nguoi goi phai coi la that bai.
     * Day la cach giu tai xe nguyen tu ma khong can khoa (FR-DRV-012, FR-DRV-013).
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update DriverProfile d
            set d.availability = :to, d.availabilityChangedAt = :now, d.version = d.version + 1
            where d.id = :id and d.availability = :from
            """)
    int transition(@Param("id") UUID id, @Param("from") DriverAvailability from,
                   @Param("to") DriverAvailability to, @Param("now") Instant now);

    // Ket thuc offer/chuyen: tai xe bi dinh chi trong luc do thi ve OFFLINE chu khong ve AVAILABLE
    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE driver_profiles
            SET availability = CASE WHEN review_status = 'APPROVED' THEN 'AVAILABLE' ELSE 'OFFLINE' END,
                availability_changed_at = :now, version = version + 1
            WHERE id = :id AND availability = :from
            """, nativeQuery = true)
    int releaseFrom(@Param("id") UUID id, @Param("from") String from, @Param("now") Instant now);

    @Query("""
            select new org.example.ride_hailing_logistics.driver.EligibleDriverRow(d.id, v.id, v.vehicleType)
            from DriverProfile d, Vehicle v
            where v.driverId = d.id and v.status = :vehicleStatus
              and d.id in :ids
              and d.availability = :availability
              and d.reviewStatus = :reviewStatus
              and v.vehicleType = :vehicleType
            """)
    List<EligibleDriverRow> findEligible(@Param("ids") Collection<UUID> ids, @Param("vehicleType") VehicleType vehicleType,
                                         @Param("vehicleStatus") VehicleStatus vehicleStatus,
                                         @Param("availability") DriverAvailability availability,
                                         @Param("reviewStatus") ReviewStatus reviewStatus);

    /** Ung vien: dang AVAILABLE, ho so APPROVED, xe dang dung dung loai (FR-MAT-002, FR-MAT-005). */
    default List<EligibleDriverRow> findEligible(Collection<UUID> ids, VehicleType vehicleType) {
        return findEligible(ids, vehicleType, VehicleStatus.ACTIVE, DriverAvailability.AVAILABLE, ReviewStatus.APPROVED);
    }

    // Doc thang tu DB (truy van scalar khong di qua persistence context), dung sau cac cap nhat co dieu kien.
    // Khong dung clearAutomatically o tren vi no detach Trip/DriverOffer dang sua trong cung transaction dispatch.
    @Query("select d.availability from DriverProfile d where d.id = :id")
    java.util.Optional<DriverAvailability> currentAvailability(@Param("id") UUID id);

    long countByAvailability(DriverAvailability availability);

    long countByIdInAndAvailability(Collection<UUID> ids, DriverAvailability availability);
}
