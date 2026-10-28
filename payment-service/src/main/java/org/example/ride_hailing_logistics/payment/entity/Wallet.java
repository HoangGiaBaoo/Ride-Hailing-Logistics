package org.example.ride_hailing_logistics.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.ride_hailing_logistics.payment.enums.WalletStatus;
import java.time.Instant;
import java.util.UUID;

/** Vi khong luu so du: so du la tong cac but toan (FR-WAL-002), nen khong the "chinh tay" lech ledger. */
@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet {

    @Id
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    // Wrapper (khong phai long): version null = entity moi -> Spring Data goi persist() chu khong merge()
    private Long version;

    public static Wallet open(UUID driverId, String currency, Instant now) {
        Wallet w = new Wallet();
        w.id = UUID.randomUUID();
        w.driverId = driverId;
        w.currency = currency;
        w.status = WalletStatus.ACTIVE;
        w.createdAt = now;
        return w;
    }
}
