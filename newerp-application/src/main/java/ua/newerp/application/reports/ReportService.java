package ua.newerp.application.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ua.newerp.domain.common.DomainException;
import ua.newerp.domain.orders.CustomerRisk;
import ua.newerp.domain.reports.ReportGrouping;
import ua.newerp.domain.reports.SaleFact;
import ua.newerp.domain.reports.SalesPeriod;
import ua.newerp.domain.reports.SalesReport;

/**
 * The home page summary and the sales and product reports, as the old site's home page and "Звіти".
 * Cancelled orders ("Відмінено") do not count unless the status is chosen explicitly; the old site counted them.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public static final int MAX_DAYS = 3 * 366;
    public static final int MAX_PRODUCTS = 200;

    private final ReportStore store;
    private final Clock clock;

    public ReportService(ReportStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public SalesReportDto sales(ReportFilter filter) {
        ReportGrouping grouping = ReportGrouping.parse(filter.group());
        DateRange range = range(filter);
        List<SaleFact> facts = store.sales(start(range.from()), start(range.to().plusDays(1)), filter.statusId(), CustomerRisk.CANCELLED_STATUS_ID);
        List<SalesReportRow> rows = SalesReport.group(facts, grouping, range.from(), range.to(), false).stream().map(ReportService::toRow).toList();
        return new SalesReportDto(range.from(), range.to(), rows, rows.stream().mapToInt(SalesReportRow::orders).sum(),
                rows.stream().mapToInt(SalesReportRow::products).sum(), sum(rows.stream().map(SalesReportRow::total).toList()));
    }

    public ProductSalesDto products(ReportFilter filter) {
        DateRange range = range(filter);
        return new ProductSalesDto(range.from(), range.to(), store.products(start(range.from()), start(range.to().plusDays(1)), filter.statusId(),
                CustomerRisk.CANCELLED_STATUS_ID, MAX_PRODUCTS));
    }

    private record Totals(int orders, BigDecimal total) {
    }

    public DashboardDto dashboard() {
        LocalDate today = today();
        LocalDate monthStart = today.withDayOfMonth(1);
        // The month is compared with the same days of the previous one: 1–20 October against 1–20 September.
        LocalDate prevMonthStart = monthStart.minusMonths(1);
        LocalDate prevMonthEnd = min(prevMonthStart.plusDays(today.getDayOfMonth() - 1L), monthStart.minusDays(1));
        LocalDate chartFrom = today.minusDays(29);

        List<SaleFact> facts = store.sales(start(min(prevMonthStart, chartFrom)), start(today.plusDays(1)), null, CustomerRisk.CANCELLED_STATUS_ID);
        Totals day = totals(facts, today, today);
        Totals yesterday = totals(facts, today.minusDays(1), today.minusDays(1));
        Totals month = totals(facts, monthStart, today);
        Totals prevMonth = totals(facts, prevMonthStart, prevMonthEnd);
        int customers = store.newCustomers(start(monthStart), start(today.plusDays(1)));
        int prevCustomers = store.newCustomers(start(prevMonthStart), start(prevMonthEnd.plusDays(1)));
        List<ProductSalesRow> top = store.products(start(chartFrom), start(today.plusDays(1)), null, CustomerRisk.CANCELLED_STATUS_ID, 5);

        return new DashboardDto(
                tile(BigDecimal.valueOf(day.orders()), BigDecimal.valueOf(yesterday.orders())),
                tile(day.total(), yesterday.total()),
                tile(BigDecimal.valueOf(month.orders()), BigDecimal.valueOf(prevMonth.orders())),
                tile(month.total(), prevMonth.total()),
                tile(average(month), average(prevMonth)),
                tile(BigDecimal.valueOf(customers), BigDecimal.valueOf(prevCustomers)),
                SalesReport.group(facts, ReportGrouping.DAY, chartFrom, today, true).stream().map(ReportService::toRow).toList(),
                top);
    }

    /** The revenue feed: today, yesterday, the month and the year so far, and the last {@code days} days one by one. */
    public RevenueFeedDto revenueFeed(int days, int maxDays) {
        if (days < 1 || days > maxDays) {
            throw new DomainException("Кількість днів — від 1 до " + maxDays + ".");
        }
        LocalDate today = today();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate prevMonthStart = monthStart.minusMonths(1);
        LocalDate prevMonthEnd = min(prevMonthStart.plusDays(today.getDayOfMonth() - 1L), monthStart.minusDays(1));
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate seriesFrom = today.minusDays(days - 1L);

        LocalDate from = min(min(prevMonthStart, yearStart), seriesFrom);
        List<SaleFact> facts = store.sales(start(from), start(today.plusDays(1)), null, CustomerRisk.CANCELLED_STATUS_ID);
        List<RevenueFeedDto.Day> series = SalesReport.group(facts, ReportGrouping.DAY, seriesFrom, today, true).stream()
                .map(p -> new RevenueFeedDto.Day(p.start(), p.orders(), p.products(), p.total()))
                .toList();
        return new RevenueFeedDto(LocalDateTime.now(clock), "UAH", feedTotals(facts, today, today),
                feedTotals(facts, today.minusDays(1), today.minusDays(1)), feedTotals(facts, monthStart, today),
                feedTotals(facts, prevMonthStart, prevMonthEnd), feedTotals(facts, yearStart, today), series);
    }

    private static RevenueFeedDto.Totals feedTotals(List<SaleFact> facts, LocalDate from, LocalDate to) {
        Totals t = totals(facts, from, to);
        return new RevenueFeedDto.Totals(from, to, t.orders(), t.total(), average(t));
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    private DateRange range(ReportFilter filter) {
        LocalDate today = today();
        // By default the current month, as in the old reports.
        LocalDate from = filter.from() != null ? filter.from() : today.withDayOfMonth(1);
        LocalDate to = filter.to() != null ? filter.to() : today;
        if (to.isBefore(from)) {
            throw new DomainException("Дата «по» раніша за дату «з».");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new DomainException("Період звіту — не більше трьох років.");
        }
        return new DateRange(from, to);
    }

    private static Totals totals(List<SaleFact> facts, LocalDate from, LocalDate to) {
        List<SaleFact> list = facts.stream()
                .filter(f -> !f.dateAdded().toLocalDate().isBefore(from) && !f.dateAdded().toLocalDate().isAfter(to))
                .toList();
        return new Totals(list.size(), sum(list.stream().map(SaleFact::total).toList()));
    }

    private static BigDecimal average(Totals t) {
        return t.orders() == 0 ? BigDecimal.ZERO : t.total().divide(BigDecimal.valueOf(t.orders()), 2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static DashboardTile tile(BigDecimal current, BigDecimal previous) {
        return new DashboardTile(current, previous, SalesReport.changePercent(current, previous));
    }

    private static SalesReportRow toRow(SalesPeriod p) {
        return new SalesReportRow(p.start(), p.end(), p.orders(), p.products(), p.total());
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime start(LocalDate date) {
        return date.atStartOfDay();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
