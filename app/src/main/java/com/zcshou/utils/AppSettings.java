package com.zcshou.utils;

public final class AppSettings {
    public static final String LOG_OFF = "setting_log_off";
    public static final String HISTORY_EXPIRATION = "setting_history_expiration";

    private AppSettings() {
    }

    public static long historyExpirationSeconds(String value) {
        try {
            double days = InputUtils.parseFiniteDouble(value);
            if (days >= 0 && days <= Long.MAX_VALUE / 86400.0) {
                return (long) (days * 86400);
            }
        } catch (NumberFormatException ignored) {
            // Invalid persisted settings must not purge the user's history.
        }
        return 7L * 86400;
    }
}
