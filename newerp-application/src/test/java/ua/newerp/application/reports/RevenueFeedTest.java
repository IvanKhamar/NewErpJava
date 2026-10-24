package ua.newerp.application.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.newerp.domain.reports.SaleFact;

class RevenueFeedTest {

    final ReportStore store = mock(ReportStore.class);
    final Clock clock = Clock.fixed(LocalDateTime.of(2026, 10, 24, 15, 0).atZone(ZoneId.of("Europe/Kyiv")).toInstant(), ZoneId.of("Europe/Kyiv"));
    final ReportService service = new ReportService(store, clock);

    static SaleFact sale(String date, String total) {
        return new SaleFact(LocalDate.parse(date).atTime(12, 0), new BigDecimal(total), 1);
    }

    @Test
    void totalsAndADailySeries() {
        when(store.sales(any(), any(), isNull(), anyInt())).thenReturn(List.of(
                sale("2026-01-10", "500"), sale("2026-09-20", "300"), sale("2026-09-30", "999"),
                sale("2026-10-01", "1000"), sale("2026-10-23", "200"), sale("2026-10-24", "400"), sale("2026-10-24", "600")));

        RevenueFeedDto feed = service.revenueFeed(7, 366);

        assertThat(feed.today().orders()).isEqualTo(2);
        assertThat(feed.today().revenue()).isEqualByComparingTo("1000");
        assertThat(feed.today().averageCheck()).isEqualByComparingTo("500");
        assertThat(feed.yesterday().revenue()).isEqualByComparingTo("200");
        assertThat(feed.month().revenue()).isEqualByComparingTo("2200");
        // 1–24 September only: the 30th is after the same day
        assertThat(feed.previousMonth().revenue()).isEqualByComparingTo("300");
        assertThat(feed.previousMonth().to()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(feed.year().orders()).isEqualTo(7);
        assertThat(feed.days()).hasSize(7);
        assertThat(feed.days().getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(feed.days().getLast().revenue()).isEqualByComparingTo("1000");
        assertThat(feed.currency()).isEqualTo("UAH");

        assertThatThrownBy(() -> service.revenueFeed(0, 366)).hasMessage("Кількість днів — від 1 до 366.");
    }
}
