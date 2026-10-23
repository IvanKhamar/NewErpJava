package ua.newerp.domain.marketing;

import java.time.LocalDate;
import java.time.YearMonth;

public final class Birthdays {

    private Birthdays() {
    }

    /**
     * The nearest (today or later) date of the birthday. February 29 in a common year is February 28.
     * Null for an impossible date (April 31 and so on).
     */
    public static LocalDate next(int day, int month, LocalDate today) {
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        if (day > YearMonth.of(2024, month).lengthOfMonth()) { // 2024 is a leap year: so 29.02 counts as a possible date
            return null;
        }
        LocalDate date = onYear(day, month, today.getYear());
        return date.isBefore(today) ? onYear(day, month, today.getYear() + 1) : date;
    }

    /** Whether the birthday falls in the period. Unlike the old code, "week" holds today's birthdays too and crosses New Year. */
    public static boolean inPeriod(int day, int month, BirthdayPeriod period, LocalDate today) {
        LocalDate next = next(day, month, today);
        if (next == null) {
            return false;
        }
        return switch (period) {
            case TODAY -> next.equals(today);
            case TOMORROW -> next.equals(today.plusDays(1));
            case WEEK -> !next.isAfter(today.plusDays(7));
            case MONTH -> month == today.getMonthValue();
            case ALL -> true;
        };
    }

    private static LocalDate onYear(int day, int month, int year) {
        return LocalDate.of(year, month, Math.min(day, YearMonth.of(year, month).lengthOfMonth()));
    }
}
