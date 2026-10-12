package com.zcshou.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class MapUtilsTest {
    @Test
    public void domesticRoundTripsKeepSubCentimeterPrecision() {
        double[][] locations = {{116.397, 39.908}, {121.4737, 31.2304}, {114.0579, 22.5431},
                {105.0, 35.0}, {121.5654, 25.0330}, {114.1694, 22.3193}};
        for (double[] original : locations) {
            double[] converted = MapUtils.wgs2bd09(original[0], original[1]);
            assertTrue(Math.abs(converted[0] - original[0]) > 0.001);
            for (int cycle = 0; cycle < 100; cycle++) {
                double[] wgs = MapUtils.bd2wgs(converted[0], converted[1]);
                assertEquals(original[0], wgs[0], 1e-8);
                assertEquals(original[1], wgs[1], 1e-8);
                converted = MapUtils.wgs2bd09(wgs[0], wgs[1]);
            }
        }
    }

    @Test
    public void overseasAndPoleCoordinatesAreNotOffset() {
        for (double[] coordinate : new double[][] {{-0.1276, 51.5072}, {-74.006, 40.7128},
                {151.2093, -33.8688}, {103.8198, 1.3521}, {126.978, 37.5665}, {100.5018, 13.7563},
                {85.324, 27.7172}, {106.9057, 47.8864}, {0, 90}, {0, -90}, {180, 0}, {0, 0}}) {
            assertArrayEquals(coordinate, MapUtils.wgs2bd09(coordinate[0], coordinate[1]), 0);
            assertArrayEquals(coordinate, MapUtils.bd2wgs(coordinate[0], coordinate[1]), 0);
        }
    }

    @Test
    public void coastalLocationsKeepRegionalMapCoordinates() {
        for (double[] coordinate : new double[][] {{114.1694, 22.3193}, {113.5439, 22.1987},
                {121.5654, 25.0330}, {109.5119, 18.2528}, {118.0894, 24.4798}, {121.6147, 38.9140}}) {
            assertTrue(java.util.Arrays.toString(coordinate), MapUtils.usesBaiduCoordinates(coordinate[0], coordinate[1]));
        }
    }

    @Test
    public void meterOffsetsRespectLatitude() {
        for (double latitude : new double[] {0, 36, 60, 85}) {
            double[] point = MapUtils.offsetMeters(10, latitude, 100, 0);
            assertEquals(100, distance(10, latitude, point[0], point[1]), 0.001);
        }
    }

    @Test
    public void movementCrossesDateLineAndPolesWithoutInvalidCoordinates() {
        double[] dateLine = MapUtils.destination(179.9999, 0, 100, 90);
        assertTrue(dateLine[0] < -179);
        assertEquals(100, distance(179.9999, 0, dateLine[0], dateLine[1]), 0.001);
        for (double latitude : new double[] {89.9999, 90, -90}) {
            double[] pole = MapUtils.destination(0, latitude, 100, 0);
            assertTrue(MapUtils.isValidCoordinate(pole[0], pole[1]));
            assertEquals(100, distance(0, latitude, pole[0], pole[1]), 0.02);
        }
    }

    @Test
    public void invalidInputsCannotReachTheLocationService() {
        for (double[] coordinate : new double[][] {{181, 0}, {0, 91}, {Double.NaN, 0}, {0, Double.POSITIVE_INFINITY}}) {
            assertFalse(MapUtils.isValidCoordinate(coordinate[0], coordinate[1]));
            assertThrows(IllegalArgumentException.class, () -> MapUtils.bd2wgs(coordinate[0], coordinate[1]));
            assertThrows(IllegalArgumentException.class, () -> MapUtils.wgs2bd09(coordinate[0], coordinate[1]));
        }
        assertThrows(IllegalArgumentException.class, () -> MapUtils.destination(0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> MapUtils.offsetMeters(0, 0, Double.NaN, 1));
    }

    static double distance(double lon1, double lat1, double lon2, double lat2) {
        double dlat = Math.toRadians(lat2 - lat1);
        double dlon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dlon / 2), 2);
        return 6371008.8 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
