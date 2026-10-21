package org.example.ride_hailing_logistics.wallet;

public final class WalletEnums {

    private WalletEnums() {
    }

    public enum WalletStatus { ACTIVE, FROZEN }

    /** Loai but toan. So tien co dau: thu nhap duong, hoa hong am (FR-WAL-006). */
    public enum EntryType { TRIP_EARNING, CANCELLATION_EARNING, COMMISSION, ADJUSTMENT }

    public enum ReferenceType { PAYMENT, ADJUSTMENT }

    public enum EntryStatus { POSTED }
}
