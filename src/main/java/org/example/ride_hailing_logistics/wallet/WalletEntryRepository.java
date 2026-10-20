package org.example.ride_hailing_logistics.wallet;

import org.example.ride_hailing_logistics.wallet.WalletEnums.EntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, UUID> {

    @Query("select coalesce(sum(e.amount), 0) from WalletEntry e where e.walletId = :walletId")
    BigDecimal balanceOf(@Param("walletId") UUID walletId);

    boolean existsByWalletIdAndEntryTypeAndReferenceId(UUID walletId, EntryType entryType, String referenceId);

    Page<WalletEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);
}
