package com.zcshou.utils;

/** Mutable only on the location thread; consumers receive immutable positions. */
public final class MockLocationEngine {
    public static final double MAX_SPEED_METERS_PER_SECOND = 1000;
    private double longitude;
    private double latitude;
    private double altitude;
    private double speed;
    private double bearing;
    private long lastElapsedMillis;
    private boolean initialized;

    public void setPosition(double lng, double lat, double alt, long elapsedMillis) {
        if (!MapUtils.isValidCoordinate(lng, lat) || !Double.isFinite(alt)) {
            throw new IllegalArgumentException("Invalid position");
        }
        longitude = lng;
        latitude = lat;
        altitude = alt;
        speed = 0;
        bearing = 0;
        lastElapsedMillis = elapsedMillis;
        initialized = true;
    }

    public void setMotion(double metersPerSecond, double bearingDegrees, long elapsedMillis) {
        if (!Double.isFinite(metersPerSecond) || metersPerSecond < 0
                || metersPerSecond > MAX_SPEED_METERS_PER_SECOND || !Double.isFinite(bearingDegrees)) {
            throw new IllegalArgumentException("Invalid motion");
        }
        advance(elapsedMillis);
        speed = metersPerSecond;
        if (speed > 0) {
            bearing = ((bearingDegrees % 360) + 360) % 360;
        }
    }

    public void step(double distanceMeters, double bearingDegrees, long elapsedMillis) {
        advance(elapsedMillis);
        double[] position = MapUtils.destination(longitude, latitude, distanceMeters, bearingDegrees);
        longitude = position[0];
        latitude = position[1];
        speed = 0;
        bearing = ((bearingDegrees % 360) + 360) % 360;
    }

    public Position advance(long elapsedMillis) {
        if (!initialized) {
            throw new IllegalStateException("Position has not been supplied");
        }
        long deltaMillis = Math.max(0, elapsedMillis - lastElapsedMillis);
        lastElapsedMillis = Math.max(lastElapsedMillis, elapsedMillis);
        if (speed > 0 && deltaMillis > 0) {
            double[] position = MapUtils.destination(longitude, latitude, speed * (deltaMillis / 1000.0), bearing);
            longitude = position[0];
            latitude = position[1];
        }
        return new Position(longitude, latitude, altitude, speed, bearing);
    }

    public static final class Position {
        public final double longitude;
        public final double latitude;
        public final double altitude;
        public final double speed;
        public final double bearing;

        private Position(double longitude, double latitude, double altitude, double speed, double bearing) {
            this.longitude = longitude;
            this.latitude = latitude;
            this.altitude = altitude;
            this.speed = speed;
            this.bearing = bearing;
        }
    }
}
