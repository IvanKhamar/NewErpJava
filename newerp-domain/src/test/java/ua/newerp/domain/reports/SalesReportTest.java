package ua.newerp.domain.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SalesReportTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 20);

    private static final List<SaleFact> FACTS = List.of(
            new SaleFact(LocalDateTime.of(2026, 10, 1, 9, 0), new BigDecimal("1000"), 2), // Thursday
            new SaleFact(LocalDateTime.of(2026, 10, 5, 23, 59), new BigDecimal("500"), 1), // Monday
            new SaleFact(LocalDateTime.of(2026, 10, 11, 12, 0), new BigDecimal("300"), 1), // Sunday
            new SaleFact(LocalDateTime.of(2026, 10, 21, 0, 0), new BigDecimal("999"), 9)); // outside the period

    @Test
    void weekStartsOnMondayAndEdgesAreClippedToTheRange() {
        assertThat(SalesReport.group(FACTS, ReportGrouping.WEEK, FROM, TO, false)).containsExactly(
                new SalesPeriod(FROM, LocalDate.of(2026, 10, 4), 1, 2, new BigDecimal("1000")),
                new SalesPeriod(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11), 2, 2, new BigDecimal("800")));
    }

    @Test
    void fillGapsAddsEmptyDaysForTheChart() {
        List<SalesPeriod> rows = SalesReport.group(FACTS, ReportGrouping.DAY, FROM, TO, true);

        assertThat(rows).hasSize(20);
        assertThat(rows.get(1).orders()).isZero();
        assertThat(rows.stream().map(SalesPeriod::total).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1800");
    }

    @Test
    void monthAndYearGroup() {
        assertThat(SalesReport.group(FACTS, ReportGrouping.MONTH, FROM, TO, false)).singleElement()
                .extracting(SalesPeriod::total).isEqualTo(new BigDecimal("1800"));
        assertThat(SalesReport.periodStart(LocalDate.of(2026, 7, 3), ReportGrouping.YEAR)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(SalesReport.group(FACTS, ReportGrouping.DAY, TO, FROM, false)).isEmpty();
        assertThat(ReportGrouping.parse("Month")).isEqualTo(ReportGrouping.MONTH);
        assertThat(ReportGrouping.parse(null)).isEqualTo(ReportGrouping.WEEK);
    }

    @ParameterizedTest
    @CsvSource({"150, 100, 50", "50, 100, -50", "1, 3, -67"})
    void changePercentIsRounded(int current, int previous, int expected) {
        assertThat(SalesReport.changePercent(BigDecimal.valueOf(current), BigDecimal.valueOf(previous))).isEqualTo(expected);
    }

    @Test
    void changePercentIsNullWhenNothingBefore() {
        assertThat(SalesReport.changePercent(BigDecimal.valueOf(5), BigDecimal.ZERO)).isNull();
    }
}
