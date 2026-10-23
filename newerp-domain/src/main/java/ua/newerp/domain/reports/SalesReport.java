package ua.newerp.domain.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The sales report by periods, as "Звіти → Продажі" of the old site. */
public final class SalesReport {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SalesReport() {
    }

    /** The first day of the period a date belongs to. A week starts on Monday, as in Ukraine (MySQL WEEK() in the old report counted from Sunday). */
    public static LocalDate periodStart(LocalDate date, ReportGrouping grouping) {
        return switch (grouping) {
            case DAY -> date;
            case WEEK -> date.minusDays(date.getDayOfWeek().getValue() - 1);
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    private static LocalDate nextPeriod(LocalDate start, ReportGrouping grouping) {
        return switch (grouping) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
            case YEAR -> start.plusYears(1);
        };
    }

    private record Bucket(int orders, int products, BigDecimal total) {
    }

    /**
     * Groups orders by periods within [from; to]. The edges of the outer periods are clipped to from and to.
     * The old report did not show empty periods; for a chart they can be added with zeros (fillGaps).
     */
    public static List<SalesPeriod> group(Collection<SaleFact> facts, ReportGrouping grouping, LocalDate from, LocalDate to, boolean fillGaps) {
        if (to.isBefore(from)) {
            return List.of();
        }
        Map<LocalDate, Bucket> buckets = new HashMap<>();
        for (SaleFact fact : facts) {
            LocalDate date = fact.dateAdded().toLocalDate();
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }
            buckets.merge(periodStart(date, grouping), new Bucket(1, fact.products(), fact.total()),
                    (a, b) -> new Bucket(a.orders() + b.orders(), a.products() + b.products(), a.total().add(b.total())));
        }

        var rows = new ArrayList<SalesPeriod>();
        for (LocalDate start = periodStart(from, grouping); !start.isAfter(to); start = nextPeriod(start, grouping)) {
            LocalDate end = nextPeriod(start, grouping).minusDays(1);
            LocalDate clippedStart = start.isBefore(from) ? from : start;
            LocalDate clippedEnd = end.isAfter(to) ? to : end;
            Bucket bucket = buckets.get(start);
            if (bucket != null) {
                rows.add(new SalesPeriod(clippedStart, clippedEnd, bucket.orders(), bucket.products(), bucket.total()));
            } else if (fillGaps) {
                rows.add(new SalesPeriod(clippedStart, clippedEnd, 0, 0, BigDecimal.ZERO));
            }
        }
        return rows;
    }

    /** The change in percent against the previous value; null when there is nothing to compare with (it was 0). */
    public static Integer changePercent(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(HUNDRED).divide(previous, 0, RoundingMode.HALF_UP).intValueExact();
    }
}
