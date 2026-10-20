package org.example.ride_hailing_logistics.pricing;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {

    @Query("""
            select r from PricingRule r
            where r.active = true and r.serviceType = :service and r.vehicleType = :vehicle and r.regionCode = :region
              and r.effectiveFrom <= :at and (r.effectiveTo is null or r.effectiveTo > :at)
            order by r.ruleVersion desc
            """)
    List<PricingRule> findEffective(@Param("service") ServiceType service, @Param("vehicle") VehicleType vehicle,
                                    @Param("region") String region, @Param("at") Instant at);

    default Optional<PricingRule> findEffectiveAt(ServiceType service, VehicleType vehicle, String region, Instant at) {
        return findEffective(service, vehicle, region, at).stream().findFirst();
    }

    /** Hai khoang [from, to) giao nhau khi from1 < to2 va from2 < to1; to null = vo han (FR-PRI-004). */
    @Query("""
            select r from PricingRule r
            where r.active = true and r.serviceType = :service and r.vehicleType = :vehicle and r.regionCode = :region
              and (r.effectiveTo is null or r.effectiveTo > :from) and r.effectiveFrom < :to
            """)
    List<PricingRule> findOverlappingBounded(@Param("service") ServiceType service, @Param("vehicle") VehicleType vehicle,
                                             @Param("region") String region, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select r from PricingRule r
            where r.active = true and r.serviceType = :service and r.vehicleType = :vehicle and r.regionCode = :region
              and (r.effectiveTo is null or r.effectiveTo > :from)
            """)
    List<PricingRule> findOverlappingOpenEnded(@Param("service") ServiceType service, @Param("vehicle") VehicleType vehicle,
                                               @Param("region") String region, @Param("from") Instant from);

    // Tach hai truy van: PostgreSQL khong suy duoc kieu cua tham so null trong "(:to is null or ...)"
    default List<PricingRule> findOverlapping(ServiceType service, VehicleType vehicle, String region, Instant from, Instant to) {
        return to == null ? findOverlappingOpenEnded(service, vehicle, region, from)
                : findOverlappingBounded(service, vehicle, region, from, to);
    }

    @Query("""
            select coalesce(max(r.ruleVersion), 0) from PricingRule r
            where r.serviceType = :service and r.vehicleType = :vehicle and r.regionCode = :region
            """)
    int maxVersion(@Param("service") ServiceType service, @Param("vehicle") VehicleType vehicle, @Param("region") String region);

    List<PricingRule> findAllByOrderByServiceTypeAscVehicleTypeAscRuleVersionDesc();
}
