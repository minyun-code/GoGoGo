package com.zcshou.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppSettingsTest {
    @Test
    public void usesConfiguredRetentionIncludingFractionalDays() {
        assertEquals(2 * 86400L, AppSettings.historyExpirationSeconds("2"));
        assertEquals(43200L, AppSettings.historyExpirationSeconds("0.5"));
        assertEquals(0, AppSettings.historyExpirationSeconds("0"));
    }

    @Test
    public void fallsBackSafelyForInvalidPersistedValues() {
        for (String value : new String[] {null, "", "-1", ".", "NaN", "Infinity", "1e100"}) {
            assertEquals(7 * 86400L, AppSettings.historyExpirationSeconds(value));
        }
    }
}
