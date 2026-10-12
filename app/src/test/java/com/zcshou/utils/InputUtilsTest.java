package com.zcshou.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class InputUtilsTest {
    @Test
    public void acceptsSignedCoordinatesAndWhitespace() {
        assertEquals(-116.397, InputUtils.parseFiniteDouble(" -116.397 "), 0);
        assertEquals(39.908, InputUtils.parseFiniteDouble("39.908"), 0);
    }

    @Test
    public void rejectsIncompleteAndNonFiniteNumbers() {
        for (String value : new String[] {"", " ", "-", ".", "1..2", "NaN", "Infinity", "-Infinity", "1e309"}) {
            assertThrows(value, NumberFormatException.class, () -> InputUtils.parseFiniteDouble(value));
        }
        assertThrows(NumberFormatException.class, () -> InputUtils.parseFiniteDouble(null));
    }
}
