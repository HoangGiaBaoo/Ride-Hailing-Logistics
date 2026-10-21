package org.example.ride_hailing_logistics.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilsTest {

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        // pi/180 x 6.371.008,8 m = 111.195,08 m
        assertEquals(111_195.08, GeoUtils.haversineMeters(0, 0, 1, 0), 0.5);
    }

    @Test
    void haversineMatchesIndependentCalculation() {
        // Nha tho Duc Ba (10.7769, 106.7009) -> DH Bach Khoa (10.7626, 106.6602): 4.721,72 m (tinh doc lap bang Python)
        assertEquals(4_721.72, GeoUtils.haversineMeters(10.7769, 106.7009, 10.7626, 106.6602), 0.5);
    }

    @Test
    void distanceIsSymmetricAndZeroForSamePoint() {
        double ab = GeoUtils.haversineMeters(10.7769, 106.7009, 10.7626, 106.6602);
        double ba = GeoUtils.haversineMeters(10.7626, 106.6602, 10.7769, 106.7009);
        assertEquals(ab, ba, 1e-9);
        assertEquals(0, GeoUtils.haversineMeters(10.7769, 106.7009, 10.7769, 106.7009), 1e-9);
    }

    @Test
    void geohashMatchesPublishedReference() {
        // Vi du kinh dien cua geohash (Wikipedia): (57.64911, 10.40744) -> u4pruydqqvj
        assertEquals("u4pruydqqvj", GeoUtils.geohash(57.64911, 10.40744, 11));
        assertEquals("u4pru", GeoUtils.geohash(57.64911, 10.40744, 5));
    }

    @Test
    void geohashCellContainsItsOwnCenter() {
        String cell = GeoUtils.geohash(10.7769, 106.7009, 5);
        double[] center = GeoUtils.geohashCenterAndRadius(cell);
        assertEquals(cell, GeoUtils.geohash(center[0], center[1], 5));
        // o do chinh xac 5 ~ 4,9 km x 4,9 km -> nua duong cheo ~3,4 km
        assertTrue(center[2] > 2_500 && center[2] < 4_000, "radius " + center[2]);
    }
}
