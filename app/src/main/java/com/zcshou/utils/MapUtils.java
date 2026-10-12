package com.zcshou.utils;

public final class MapUtils {
    private static final double EARTH_RADIUS_METERS = 6371008.8;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;
    private static final double X_PI = Math.PI * 3000.0 / 180.0;

    private MapUtils() {
    }

    public static boolean isValidCoordinate(double longitude, double latitude) {
        return Double.isFinite(longitude) && Double.isFinite(latitude)
                && longitude >= -180 && longitude <= 180 && latitude >= -90 && latitude <= 90;
    }

    private static void requireCoordinate(double longitude, double latitude) {
        if (!isValidCoordinate(longitude, latitude)) {
            throw new IllegalArgumentException("Invalid coordinates");
        }
    }

    // A rectangle alone incorrectly offsets overseas locations such as Singapore and Seoul.
    public static boolean usesBaiduCoordinates(double longitude, double latitude) {
        return CoordinateRegion.contains(longitude, latitude);
    }

    public static double[] bd2wgs(double longitude, double latitude) {
        requireCoordinate(longitude, latitude);
        if (!usesBaiduCoordinates(longitude, latitude)) {
            return new double[] {longitude, latitude};
        }
        double[] gcj = bd09togcj02(longitude, latitude);
        return gcj02towgs84(gcj[0], gcj[1]);
    }

    public static double[] wgs2bd09(double longitude, double latitude) {
        requireCoordinate(longitude, latitude);
        if (!usesBaiduCoordinates(longitude, latitude)) {
            return new double[] {longitude, latitude};
        }
        double[] gcj = wgs84togcj02(longitude, latitude);
        return gcj02tobd09(gcj[0], gcj[1]);
    }

    private static double[] gcj02tobd09(double longitude, double latitude) {
        double z = Math.hypot(longitude, latitude) + 0.00002 * Math.sin(latitude * X_PI);
        double theta = Math.atan2(latitude, longitude) + 0.000003 * Math.cos(longitude * X_PI);
        return new double[] {z * Math.cos(theta) + 0.0065, z * Math.sin(theta) + 0.006};
    }

    public static double[] bd09togcj02(double longitude, double latitude) {
        requireCoordinate(longitude, latitude);
        double x = longitude - 0.0065;
        double y = latitude - 0.006;
        double z = Math.hypot(x, y) - 0.00002 * Math.sin(y * X_PI);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI);
        double lng = z * Math.cos(theta);
        double lat = z * Math.sin(theta);
        for (int i = 0; i < 10; i++) {
            double[] projected = gcj02tobd09(lng, lat);
            double errorLng = projected[0] - longitude;
            double errorLat = projected[1] - latitude;
            lng -= errorLng;
            lat -= errorLat;
            if (Math.max(Math.abs(errorLng), Math.abs(errorLat)) < 1e-10) {
                break;
            }
        }
        return new double[] {lng, lat};
    }

    private static double[] wgs84togcj02(double longitude, double latitude) {
        double dlat = transformLat(longitude - 105.0, latitude - 35.0);
        double dlng = transformLon(longitude - 105.0, latitude - 35.0);
        double radlat = Math.toRadians(latitude);
        double magic = Math.sin(radlat);
        magic = 1 - EE * magic * magic;
        double sqrtmagic = Math.sqrt(magic);
        dlat = dlat * 180.0 / (A * (1 - EE) / (magic * sqrtmagic) * Math.PI);
        dlng = dlng * 180.0 / (A / sqrtmagic * Math.cos(radlat) * Math.PI);
        return new double[] {longitude + dlng, latitude + dlat};
    }

    public static double[] gcj02towgs84(double longitude, double latitude) {
        requireCoordinate(longitude, latitude);
        if (!usesBaiduCoordinates(longitude, latitude)) {
            return new double[] {longitude, latitude};
        }
        double lng = longitude;
        double lat = latitude;
        for (int i = 0; i < 20; i++) {
            double[] projected = wgs84togcj02(lng, lat);
            double errorLng = projected[0] - longitude;
            double errorLat = projected[1] - latitude;
            lng -= errorLng;
            lat -= errorLat;
            if (Math.max(Math.abs(errorLng), Math.abs(errorLat)) < 1e-10) {
                break;
            }
        }
        return new double[] {lng, lat};
    }

    /** Move on the globe without dividing by cos(latitude) at the poles. */
    public static double[] destination(double longitude, double latitude,
                                       double distanceMeters, double bearingDegrees) {
        requireCoordinate(longitude, latitude);
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0 || !Double.isFinite(bearingDegrees)) {
            throw new IllegalArgumentException("Invalid movement");
        }
        if (distanceMeters == 0) {
            return new double[] {longitude, latitude};
        }
        double angularDistance = distanceMeters / EARTH_RADIUS_METERS;
        double bearing = Math.toRadians(bearingDegrees % 360);
        double lat = Math.toRadians(latitude);
        double lng = Math.toRadians(longitude);
        double nextLat = Math.asin(Math.max(-1, Math.min(1, Math.sin(lat) * Math.cos(angularDistance)
                + Math.cos(lat) * Math.sin(angularDistance) * Math.cos(bearing))));
        double nextLng = lng + Math.atan2(Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(lat),
                Math.cos(angularDistance) - Math.sin(lat) * Math.sin(nextLat));
        double normalizedLng = ((Math.toDegrees(nextLng) + 540) % 360) - 180;
        return new double[] {normalizedLng, Math.toDegrees(nextLat)};
    }

    public static double[] offsetMeters(double longitude, double latitude, double eastMeters, double northMeters) {
        if (!Double.isFinite(eastMeters) || !Double.isFinite(northMeters)) {
            throw new IllegalArgumentException("Invalid offset");
        }
        return destination(longitude, latitude, Math.hypot(eastMeters, northMeters),
                Math.toDegrees(Math.atan2(eastMeters, northMeters)));
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }
}
