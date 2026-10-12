package com.zcshou.utils;

public final class InputUtils {
    private InputUtils() {
    }

    public static double parseFiniteDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new NumberFormatException("Empty number");
        }
        double number = Double.parseDouble(value.trim());
        if (!Double.isFinite(number)) {
            throw new NumberFormatException("Number must be finite");
        }
        return number;
    }
}
