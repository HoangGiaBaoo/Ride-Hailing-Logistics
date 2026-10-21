package org.example.ride_hailing_logistics.common.util;

/** Ham hinh hoc tren WGS84 (DR-GEO-001). Thuan, khong phu thuoc Spring. */
public final class GeoUtils {

    public static final double EARTH_RADIUS_METERS = 6_371_008.8;
    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    private GeoUtils() {
    }

    /** Khoang cach duong chim bay (met) theo cong thuc haversine. */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static boolean isValidCoordinate(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isNaN(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    /**
     * Geohash: chia doi xen ke kinh do / vi do, moi 5 bit thanh mot ky tu base32.
     * Dung lam o vung cho surge (TBD-04): cung o = cung khu vuc cung/cau.
     */
    public static String geohash(double lat, double lng, int precision) {
        double minLat = -90, maxLat = 90, minLng = -180, maxLng = 180;
        StringBuilder hash = new StringBuilder(precision);
        boolean evenBit = true;   // bit chan la kinh do
        int bit = 0;
        int ch = 0;
        while (hash.length() < precision) {
            if (evenBit) {
                double mid = (minLng + maxLng) / 2;
                if (lng >= mid) {
                    ch = (ch << 1) | 1;
                    minLng = mid;
                } else {
                    ch = ch << 1;
                    maxLng = mid;
                }
            } else {
                double mid = (minLat + maxLat) / 2;
                if (lat >= mid) {
                    ch = (ch << 1) | 1;
                    minLat = mid;
                } else {
                    ch = ch << 1;
                    maxLat = mid;
                }
            }
            evenBit = !evenBit;
            if (++bit == 5) {
                hash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }
        return hash.toString();
    }

    /** Tam va nua duong cheo (met) cua o geohash, dung de truy van GEO bao trum ca o. */
    public static double[] geohashCenterAndRadius(String hash) {
        double minLat = -90, maxLat = 90, minLng = -180, maxLng = 180;
        boolean evenBit = true;
        for (char c : hash.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid geohash: " + hash);
            }
            for (int mask = 16; mask > 0; mask >>= 1) {
                boolean on = (value & mask) != 0;
                if (evenBit) {
                    double mid = (minLng + maxLng) / 2;
                    if (on) minLng = mid; else maxLng = mid;
                } else {
                    double mid = (minLat + maxLat) / 2;
                    if (on) minLat = mid; else maxLat = mid;
                }
                evenBit = !evenBit;
            }
        }
        double centerLat = (minLat + maxLat) / 2;
        double centerLng = (minLng + maxLng) / 2;
        double radius = haversineMeters(centerLat, centerLng, maxLat, maxLng);
        return new double[]{centerLat, centerLng, radius};
    }
}
