package ua.newerp.web;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The same display of quantities, money and dates on all pages (Fmt.cs of the Blazor UI). */
public final class Fmt {

    public static final Locale UK = Locale.forLanguageTag("uk-UA");

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private Fmt() {
    }

    /** A quantity without extra zeros: 12,5; 100; 0,25. */
    public static String qty(BigDecimal value) {
        return value == null ? "—" : new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(UK)).format(value);
    }

    public static String money(BigDecimal value) {
        return value == null ? "—" : new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(UK)).format(value) + " ₴";
    }

    /** A number for an input field: 1500.0000 from the database becomes 1500, 12.50 becomes 12.5. */
    public static BigDecimal plain(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    public static String date(LocalDateTime value) {
        return value == null ? "—" : value.format(DATE_TIME);
    }
}
