package ua.newerp.domain.reports;

import java.util.Locale;

import ua.newerp.domain.common.DomainException;

/** How to group sales in the report (the "Групувати за" filter of the old site's sales report). */
public enum ReportGrouping {
    DAY, WEEK, MONTH, YEAR;

    /** As .NET reads an enum from a query string: the name ("Month") in any case, or the number. Empty: a week. */
    public static ReportGrouping parse(String value) {
        if (value == null || value.isBlank()) {
            return WEEK;
        }
        String text = value.strip();
        if (text.matches("\\d+")) {
            int ordinal = Integer.parseInt(text);
            if (ordinal < values().length) {
                return values()[ordinal];
            }
        } else {
            for (ReportGrouping grouping : values()) {
                if (grouping.name().equals(text.toUpperCase(Locale.ROOT))) {
                    return grouping;
                }
            }
        }
        throw new DomainException("Невідоме групування «" + text + "» (Day, Week, Month, Year).");
    }
}
