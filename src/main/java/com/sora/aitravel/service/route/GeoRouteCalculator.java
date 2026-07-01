package com.sora.aitravel.service.route;

/** Shared coordinate route calculations for generation-time route planning. */
public final class GeoRouteCalculator {
    public static final double DEFAULT_ROAD_DISTANCE_FACTOR = 1.35;
    public static final double DEFAULT_DRIVING_SPEED_KMH = 38.0;
    public static final double CITY_DRIVING_SPEED_KMH = 28.0;
    public static final double WALKING_SPEED_KMH = 4.2;

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoRouteCalculator() {}

    public static double[] parseLocation(String location) {
        if (location == null || location.isBlank() || !location.contains(",")) {
            return null;
        }
        String[] parts = location.split(",");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new double[] {
                Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())
            };
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public static int roadDistanceMeters(
            double fromLng, double fromLat, double toLng, double toLat) {
        return roadDistanceMeters(fromLng, fromLat, toLng, toLat, DEFAULT_ROAD_DISTANCE_FACTOR);
    }

    public static int roadDistanceMeters(
            double fromLng, double fromLat, double toLng, double toLat, double roadFactor) {
        return (int) Math.round(distanceKm(fromLng, fromLat, toLng, toLat) * roadFactor * 1000);
    }

    public static int drivingSeconds(int distanceMeters) {
        return travelSeconds(distanceMeters, DEFAULT_DRIVING_SPEED_KMH);
    }

    public static int travelSeconds(int distanceMeters, double speedKmh) {
        if (distanceMeters <= 0) {
            return 0;
        }
        return (int) Math.round(distanceMeters / 1000.0 / speedKmh * 3600);
    }

    public static double distanceKm(double fromLng, double fromLat, double toLng, double toLat) {
        double latDistance = Math.toRadians(toLat - fromLat);
        double lngDistance = Math.toRadians(toLng - fromLng);
        double a =
                Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                        + Math.cos(Math.toRadians(fromLat))
                                * Math.cos(Math.toRadians(toLat))
                                * Math.sin(lngDistance / 2)
                                * Math.sin(lngDistance / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
