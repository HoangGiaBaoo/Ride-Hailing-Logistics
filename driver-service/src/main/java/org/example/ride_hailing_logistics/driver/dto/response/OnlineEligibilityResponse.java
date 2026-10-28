package org.example.ride_hailing_logistics.driver.dto.response;

import java.util.List;

/**
 * Tra loi trip-service khi tai xe bam Online: du dieu kien hay chua (ly do cu the cho UI-DRV-002)
 * va ban chup thong tin tai xe + xe de trip-service luu lai, khoi goi lai moi lan ghep chuyen.
 */
public record OnlineEligibilityResponse(boolean eligible, List<String> reasons, DriverSnapshotResponse snapshot) {}
