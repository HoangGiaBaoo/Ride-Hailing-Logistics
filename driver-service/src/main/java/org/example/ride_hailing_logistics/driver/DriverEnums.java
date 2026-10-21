package org.example.ride_hailing_logistics.driver;

/** Tap trung cac enum cua mien driver de doi chieu nhanh voi docs/01-nghiep-vu.md §2-3. */
public final class DriverEnums {

    private DriverEnums() {
    }

    /** FR-DRV-003. */
    public enum ReviewStatus { DRAFT, PENDING_REVIEW, APPROVED, REJECTED, SUSPENDED }

    /** FR-DRV-009. */
    public enum DriverAvailability { OFFLINE, AVAILABLE, OFFERED, BUSY }

    public enum VehicleStatus { ACTIVE, INACTIVE }

    /** TBD-11: ba tai lieu bat buoc. */
    public enum DocumentType { DRIVER_LICENSE, NATIONAL_ID, VEHICLE_REGISTRATION }

    public enum DocumentStatus { UPLOADED, APPROVED, REJECTED, SUPERSEDED }

    public enum ReviewDecisionType { APPROVE, REJECT, REQUEST_CHANGES, SUSPEND }
}
