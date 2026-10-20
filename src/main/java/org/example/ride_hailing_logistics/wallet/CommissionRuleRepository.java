package org.example.ride_hailing_logistics.wallet;

import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, UUID> {

    @Query("""
            select r from CommissionRule r
            where r.serviceType = :type and r.effectiveFrom <= :at and (r.effectiveTo is null or r.effectiveTo > :at)
            order by r.effectiveFrom desc
            """)
    List<CommissionRule> findEffective(@Param("type") ServiceType type, @Param("at") Instant at);

    default Optional<CommissionRule> findEffectiveAt(ServiceType type, Instant at) {
        return findEffective(type, at).stream().findFirst();
    }

    @Query("select coalesce(max(r.ruleVersion), 0) from CommissionRule r where r.serviceType = :type")
    int maxVersion(@Param("type") ServiceType type);

    List<CommissionRule> findAllByOrderByServiceTypeAscEffectiveFromDesc();
}
