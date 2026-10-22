package ua.newerp.domain.common;

import java.math.BigDecimal;

/**
 * Helpers for BigDecimal, Java's type for exact money and quantities (C# decimal).
 * BigDecimal has no operators: a + b is a.add(b), and comparisons go through compareTo,
 * because equals also compares the scale (2.0 is not equal to 2.00).
 */
public final class Decimals {

    private Decimals() {
    }

    public static boolean isPositive(BigDecimal value) {
        return value.signum() > 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return value.signum() < 0;
    }

    /** a &lt; b */
    public static boolean less(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) < 0;
    }

    /** a &gt; b */
    public static boolean greater(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) > 0;
    }

    /** Number for messages to people: 5 instead of 5.000. */
    public static String text(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
