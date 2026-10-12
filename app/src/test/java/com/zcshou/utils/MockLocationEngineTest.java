package com.zcshou.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class MockLocationEngineTest {
    @Test
    public void noPositionIsPublishedUntilATargetIsProvided() {
        MockLocationEngine engine = new MockLocationEngine();
        assertThrows(IllegalStateException.class, () -> engine.advance(1000));
        engine.setPosition(121.4737, 31.2304, 55, 1000);
        MockLocationEngine.Position point = engine.advance(5000);
        assertEquals(121.4737, point.longitude, 0);
        assertEquals(31.2304, point.latitude, 0);
        assertEquals(0, point.speed, 0);
    }

    @Test
    public void partialJoystickSpeedMatchesDistanceAndElapsedTime() {
        MockLocationEngine engine = new MockLocationEngine();
        engine.setPosition(10, 0, 55, 1000);
        engine.setMotion(0.6, 90, 1000);
        MockLocationEngine.Position first = engine.advance(1100);
        assertEquals(0.06, MapUtilsTest.distance(10, 0, first.longitude, first.latitude), 1e-5);
        MockLocationEngine.Position late = engine.advance(2350);
        assertEquals(0.81, MapUtilsTest.distance(10, 0, late.longitude, late.latitude), 1e-5);
        assertEquals(0.6, late.speed, 0);
        // Retained snapshots cannot change when the engine advances.
        assertEquals(0.06, MapUtilsTest.distance(10, 0, first.longitude, first.latitude), 1e-5);
    }

    @Test
    public void releasingJoystickFreezesPositionAndReportsZeroSpeed() {
        MockLocationEngine engine = new MockLocationEngine();
        engine.setPosition(10, 0, 55, 1000);
        engine.setMotion(10, 90, 1000);
        engine.setMotion(0, 90, 1250);
        MockLocationEngine.Position stopped = engine.advance(5000);
        assertEquals(2.5, MapUtilsTest.distance(10, 0, stopped.longitude, stopped.latitude), 1e-5);
        assertEquals(0, stopped.speed, 0);
        MockLocationEngine.Position later = engine.advance(9000);
        assertEquals(stopped.longitude, later.longitude, 0);
        assertEquals(stopped.latitude, later.latitude, 0);
    }

    @Test
    public void teleportAndSingleStepDoNotKeepPreviousVelocity() {
        MockLocationEngine engine = new MockLocationEngine();
        engine.setPosition(10, 0, 55, 1000);
        engine.setMotion(10, 90, 1000);
        engine.setPosition(-74, 40, -10, 2000);
        MockLocationEngine.Position target = engine.advance(10000);
        assertEquals(-74, target.longitude, 0);
        assertEquals(40, target.latitude, 0);
        assertEquals(-10, target.altitude, 0);
        engine.step(3.6, 0, 10000);
        MockLocationEngine.Position stepped = engine.advance(11000);
        assertEquals(3.6, MapUtilsTest.distance(-74, 40, stepped.longitude, stepped.latitude), 1e-5);
        assertEquals(0, stepped.speed, 0);
    }

    @Test
    public void speedChangesIntegratePreviousVelocityAndNormalizeBearing() {
        MockLocationEngine engine = new MockLocationEngine();
        engine.setPosition(10, 0, 55, 1000);
        engine.setMotion(1, 450, 1000);
        engine.setMotion(2, -270, 2000);
        MockLocationEngine.Position point = engine.advance(3000);
        assertEquals(3, MapUtilsTest.distance(10, 0, point.longitude, point.latitude), 1e-5);
        assertEquals(90, point.bearing, 0);
        engine.advance(2500);
        MockLocationEngine.Position later = engine.advance(3500);
        assertEquals(4, MapUtilsTest.distance(10, 0, later.longitude, later.latitude), 1e-5);
    }

    @Test
    public void rejectsNonFinitePositionsAndInvalidSpeeds() {
        MockLocationEngine engine = new MockLocationEngine();
        assertThrows(IllegalArgumentException.class, () -> engine.setPosition(0, 91, 55, 0));
        assertThrows(IllegalArgumentException.class, () -> engine.setPosition(0, 0, Double.NaN, 0));
        engine.setPosition(10, 0, 55, 1000);
        for (double speed : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, 1001}) {
            assertThrows(IllegalArgumentException.class, () -> engine.setMotion(speed, 0, 1000));
        }
    }
}
