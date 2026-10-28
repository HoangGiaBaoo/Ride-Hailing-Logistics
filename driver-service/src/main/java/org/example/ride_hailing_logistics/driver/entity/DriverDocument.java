package org.example.ride_hailing_logistics.driver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.ride_hailing_logistics.driver.enums.DocumentStatus;
import org.example.ride_hailing_logistics.driver.enums.DocumentType;
import org.example.ride_hailing_logistics.driver.service.DocumentStorageService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Tai lieu tai xe (DR-PRV-003: du lieu nhay cam). Tep luu ngoai classpath, ten do server sinh. */
@Entity
@Table(name = "driver_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DriverDocument {

    @Id
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 30)
    private DocumentType docType;

    @Column(name = "doc_number", nullable = false, length = 50)
    private String docNumber;

    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus status;

    @Column(name = "storage_name", nullable = false, length = 100)
    private String storageName;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static DriverDocument create(UUID driverId, DocumentType type, String number, LocalDate expiresAt,
                                 DocumentStorageService.StoredFile file, Instant now) {
        DriverDocument d = new DriverDocument();
        d.id = UUID.randomUUID();
        d.driverId = driverId;
        d.docType = type;
        d.docNumber = number;
        d.expiresAt = expiresAt;
        d.status = DocumentStatus.UPLOADED;
        d.storageName = file.storageName();
        d.contentType = file.contentType();
        d.fileSize = file.size();
        d.sha256 = file.sha256();
        d.createdAt = now;
        return d;
    }

    public boolean isValidOn(LocalDate day) {
        return expiresAt.isAfter(day);
    }

    public void supersede() {
        status = DocumentStatus.SUPERSEDED;
    }

    public void approve() {
        status = DocumentStatus.APPROVED;
    }

    public void reject() {
        status = DocumentStatus.REJECTED;
    }

    /** So giay to la PII: chi hien 4 ky tu cuoi khi khong can day du (DR-PRV-004). */
    public String maskedNumber() {
        return docNumber.length() <= 4 ? "****" : "****" + docNumber.substring(docNumber.length() - 4);
    }
}
