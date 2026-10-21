package org.example.ride_hailing_logistics.driver;

import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentStatus;
import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentType;
import org.example.ride_hailing_logistics.driver.DriverEnums.ReviewStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quy tac du dieu kien, viet thuan de test duoc:
 * - gui duyet: du thong tin ca nhan, co xe, du 3 tai lieu con han (FR-DRV-001, FR-DRV-002, FR-DRV-004)
 * - Online: them dieu kien da duyet, tai khoan hoat dong, tai lieu da duyet (FR-DRV-008, BR-001)
 */
final class DriverEligibility {

    static final Set<DocumentType> REQUIRED_DOCUMENTS = EnumSet.allOf(DocumentType.class);

    private DriverEligibility() {
    }

    static List<String> missingForSubmission(DriverProfile profile, Vehicle vehicle, Collection<DriverDocument> documents,
                                             LocalDate today) {
        List<String> missing = new ArrayList<>();
        if (profile.getLicenseNumber() == null || profile.getNationalIdNumber() == null || profile.getAddress() == null) {
            missing.add("Thiếu thông tin cá nhân hoặc giấy phép lái xe.");
        }
        if (profile.getLicenseExpiry() != null && !profile.getLicenseExpiry().isAfter(today)) {
            missing.add("Giấy phép lái xe đã hết hạn.");
        }
        if (vehicle == null) {
            missing.add("Chưa khai báo phương tiện.");
        }
        Set<DocumentType> present = documents.stream()
                .filter(d -> d.getStatus() != DocumentStatus.SUPERSEDED && d.getStatus() != DocumentStatus.REJECTED)
                .map(DriverDocument::getDocType)
                .collect(Collectors.toSet());
        for (DocumentType type : REQUIRED_DOCUMENTS) {
            if (!present.contains(type)) {
                missing.add("Thiếu tài liệu " + type + ".");
            }
        }
        documents.stream()
                .filter(d -> d.getStatus() != DocumentStatus.SUPERSEDED && !d.isValidOn(today))
                .forEach(d -> missing.add("Tài liệu " + d.getDocType() + " đã hết hạn."));
        return missing;
    }

    static List<String> blockingOnline(boolean accountActive, DriverProfile profile, Vehicle vehicle,
                                       Collection<DriverDocument> documents, LocalDate today) {
        List<String> reasons = new ArrayList<>();
        if (!accountActive) {
            reasons.add("Tài khoản đang bị khóa.");
        }
        if (profile.getReviewStatus() != ReviewStatus.APPROVED) {
            reasons.add("Hồ sơ chưa được duyệt (trạng thái: " + profile.getReviewStatus() + ").");
        }
        if (profile.getLicenseExpiry() == null || !profile.getLicenseExpiry().isAfter(today)) {
            reasons.add("Giấy phép lái xe đã hết hạn.");
        }
        if (vehicle == null) {
            reasons.add("Chưa có phương tiện đang hoạt động.");
        }
        Set<DocumentType> valid = documents.stream()
                .filter(d -> d.getStatus() == DocumentStatus.APPROVED && d.isValidOn(today))
                .map(DriverDocument::getDocType)
                .collect(Collectors.toSet());
        for (DocumentType type : REQUIRED_DOCUMENTS) {
            if (!valid.contains(type)) {
                reasons.add("Tài liệu " + type + " chưa được duyệt hoặc đã hết hạn.");
            }
        }
        return reasons;
    }
}
