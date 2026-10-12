package com.zcshou.utils;

public final class LocationResultPolicy {
    public static final int MAX_RETRIES = 3;

    private LocationResultPolicy() {
    }

    public static boolean isUsable(int type, double longitude, double latitude, float radius) {
        return (type == 61 || type == 161 || type == 66)
                && MapUtils.isValidCoordinate(longitude, latitude) && Float.isFinite(radius) && radius >= 0;
    }

    public static long retryDelayMillis(int type, int retries) {
        if ((type != 62 && type != 63 && type != 67 && type != 167)
                || retries < 0 || retries >= MAX_RETRIES) {
            return -1;
        }
        return 1000L << retries;
    }
}
