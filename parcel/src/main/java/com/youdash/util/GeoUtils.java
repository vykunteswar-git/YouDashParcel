package com.youdash.util;

import java.util.List;

/**
 * Geographic helpers: haversine distance and point-in-polygon (ray casting).
 * Polygon vertices are [lat, lng] in degrees; ray casting uses lng as x and lat as y.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
    }

    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static boolean isInsideCircle(double pointLat, double pointLng,
            double centerLat, double centerLng, double radiusKm) {
        return haversineKm(centerLat, centerLng, pointLat, pointLng) <= radiusKm + 1e-9;
    }

    /**
     * Ray casting; {@code ring} is ordered [lat, lng] per vertex, at least 3 vertices.
     */
    public static boolean isInsidePolygon(double lat, double lng, List<double[]> ring) {
        if (ring == null || ring.size() < 3) {
            return false;
        }
        boolean inside = false;
        int n = ring.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double latI = ring.get(i)[0];
            double lngI = ring.get(i)[1];
            double latJ = ring.get(j)[0];
            double lngJ = ring.get(j)[1];
            if (Math.abs(latJ - latI) < 1e-12) {
                continue;
            }
            if ((latI > lat) != (latJ > lat)
                    && lng < (lngJ - lngI) * (lat - latI) / (latJ - latI) + lngI) {
                inside = !inside;
            }
        }
        return inside;
    }
}
