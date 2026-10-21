package org.example.ride_hailing_logistics.driver;

import org.example.ride_hailing_logistics.driver.DriverEnums.DriverAvailability;

import java.util.UUID;

/** Trang thai kha dung hien tai cung offer/chuyen dang giu tai xe (doc thang tu DB). */
public record HoldState(DriverAvailability availability, UUID ref) {

    public boolean heldBy(DriverAvailability expected, UUID expectedRef) {
        return availability == expected && expectedRef.equals(ref);
    }
}
