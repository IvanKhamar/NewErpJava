package ua.newerp.application.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Revenue for an external dashboard: placed orders except the cancelled ones, as the home page counts them. previousMonth covers
 * the same days of the previous month (1–24 September against 1–24 October). days runs from the oldest day to today.
 */
public record RevenueFeedDto(LocalDateTime generatedAt, String currency, Totals today, Totals yesterday, Totals month,
        Totals previousMonth, Totals year, List<Day> days) {

    public record Totals(LocalDate from, LocalDate to, int orders, BigDecimal revenue, BigDecimal averageCheck) {
    }

    public record Day(LocalDate date, int orders, int products, BigDecimal revenue) {
    }
}
