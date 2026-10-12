package com.zcshou.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class VersionUtilsTest {
    @Test
    public void comparesVersionNumbersInsteadOfSubstrings() {
        assertTrue(VersionUtils.isNewerVersion("v1.12.30", "1.12.3"));
        assertTrue(VersionUtils.isNewerVersion("1.13.0", "1.9.99"));
        assertFalse(VersionUtils.isNewerVersion("1.12.2", "1.12.3"));
        assertFalse(VersionUtils.isNewerVersion("v1.12.3", "1.12.3"));
    }

    @Test
    public void handlesPreviewReleasesAndBuildMetadata() {
        assertTrue(VersionUtils.isNewerVersion("1.12.3", "1.12.3-rc.2"));
        assertTrue(VersionUtils.isNewerVersion("1.12.3-rc.10", "1.12.3-rc.2"));
        assertFalse(VersionUtils.isNewerVersion("1.12.3-beta", "1.12.3"));
        assertEquals(0, VersionUtils.compare("1.12.3+build.5", "1.12.3+build.4"));
        assertEquals(0, VersionUtils.compare("1.12", "1.12.0"));
    }

    @Test
    public void rejectsInvalidMetadata() {
        for (String version : new String[] {"", "release", "1.2.x"}) {
            assertThrows(IllegalArgumentException.class, () -> VersionUtils.compare(version, "1.12.3"));
        }
    }
}
