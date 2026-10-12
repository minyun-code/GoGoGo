package com.zcshou.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class LocationResultPolicyTest {
    @Test
    public void onlySuccessfulValidResultsMayUpdateMaps() {
        for (int type : new int[] {61, 66, 161}) {
            assertTrue(LocationResultPolicy.isUsable(type, 116.397, 39.908, 10));
        }
        for (int type : new int[] {0, 62, 63, 67, 69, 70, 71, 162, 167, 505, 506}) {
            assertFalse(LocationResultPolicy.isUsable(type, 116.397, 39.908, 10));
        }
        assertFalse(LocationResultPolicy.isUsable(61, Double.NaN, 39, 10));
        assertFalse(LocationResultPolicy.isUsable(161, 116, 91, 10));
        assertFalse(LocationResultPolicy.isUsable(61, 116, 39, Float.NaN));
        assertFalse(LocationResultPolicy.isUsable(61, 116, 39, -1));
        assertTrue(LocationResultPolicy.isUsable(61, 0, 0, 0));
    }

    @Test
    public void transientFailuresUseThreeBoundedBackoffRetries() {
        for (int type : new int[] {62, 63, 67, 167}) {
            assertEquals(1000, LocationResultPolicy.retryDelayMillis(type, 0));
            assertEquals(2000, LocationResultPolicy.retryDelayMillis(type, 1));
            assertEquals(4000, LocationResultPolicy.retryDelayMillis(type, 2));
            assertEquals(-1, LocationResultPolicy.retryDelayMillis(type, 3));
            assertEquals(-1, LocationResultPolicy.retryDelayMillis(type, -1));
        }
        for (int type : new int[] {61, 69, 70, 71, 505, 506}) {
            assertEquals(-1, LocationResultPolicy.retryDelayMillis(type, 0));
        }
    }
}
