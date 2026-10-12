package com.zcshou.utils;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionUtils {
    private static final Pattern VERSION = Pattern.compile(
            "^[vV]?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");

    private VersionUtils() {
    }

    public static boolean isNewerVersion(String remote, String current) {
        return compare(remote, current) > 0;
    }

    public static int compare(String first, String second) {
        Matcher left = parse(first);
        Matcher right = parse(second);
        String[] leftNumbers = left.group(1).split("\\.");
        String[] rightNumbers = right.group(1).split("\\.");
        for (int i = 0; i < Math.max(leftNumbers.length, rightNumbers.length); i++) {
            BigInteger a = i < leftNumbers.length ? new BigInteger(leftNumbers[i]) : BigInteger.ZERO;
            BigInteger b = i < rightNumbers.length ? new BigInteger(rightNumbers[i]) : BigInteger.ZERO;
            int result = a.compareTo(b);
            if (result != 0) {
                return result;
            }
        }
        String leftPreview = left.group(2);
        String rightPreview = right.group(2);
        if (leftPreview == null || rightPreview == null) {
            return leftPreview == rightPreview ? 0 : (leftPreview == null ? 1 : -1);
        }
        String[] leftParts = leftPreview.split("\\.");
        String[] rightParts = rightPreview.split("\\.");
        for (int i = 0; i < Math.min(leftParts.length, rightParts.length); i++) {
            String a = leftParts[i];
            String b = rightParts[i];
            boolean aNumeric = a.matches("\\d+");
            boolean bNumeric = b.matches("\\d+");
            int result = aNumeric && bNumeric ? new BigInteger(a).compareTo(new BigInteger(b))
                    : (aNumeric != bNumeric ? (aNumeric ? -1 : 1) : a.compareTo(b));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    private static Matcher parse(String value) {
        Matcher matcher = VERSION.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version: " + value);
        }
        return matcher;
    }
}
