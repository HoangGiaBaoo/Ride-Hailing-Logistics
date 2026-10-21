package org.example.ride_hailing_logistics.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByDriverId(UUID driverId);

    // Khoa vi khi ghi but toan can kiem so du (dieu chinh am): tranh hai dieu chinh cung luc cung thay so du cu
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.driverId = :driverId")
    Optional<Wallet> findByDriverIdForUpdate(@Param("driverId") UUID driverId);
}
