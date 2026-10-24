package ua.newerp.web.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderStatusDto;
import ua.newerp.application.reports.ProductSalesDto;
import ua.newerp.application.reports.ReportFilter;
import ua.newerp.application.reports.ReportService;
import ua.newerp.application.reports.SalesReportDto;
import ua.newerp.application.reports.SalesReportRow;
import ua.newerp.application.reports.StatusReportDto;

/** The reports page with a mocked service; the figures are invented. */
@ViewPackages(classes = ReportsView.class)
class ReportsViewTest extends BrowserlessTest {

    final ReportService service = mock(ReportService.class);
    final OrderQueries orders = mock(OrderQueries.class);
    final LocalDate first = LocalDate.of(2026, 10, 1);
    final LocalDate last = LocalDate.of(2026, 10, 31);

    @Test
    void showsTheDefaultPeriodAndReloadsWithTheFilters() {
        when(orders.listStatuses()).thenReturn(List.of(new OrderStatusDto(5, "Виконано", "#00aa00")));
        var week = new SalesReportRow(first, LocalDate.of(2026, 10, 4), 3, 5, new BigDecimal("4200"));
        when(service.sales(any(ReportFilter.class))).thenReturn(new SalesReportDto(first, last, List.of(week), 3, 5, new BigDecimal("4200")));
        when(service.products(any(ReportFilter.class))).thenReturn(new ProductSalesDto(first, last, List.of()));
        var done = new StatusReportDto.OrderRow(5, "Виконано", 3, new BigDecimal("4200"));
        when(service.statuses(any(ReportFilter.class)))
                .thenReturn(new StatusReportDto(first, last, List.of(done), 3, new BigDecimal("4200"), List.of(), 0));
        var view = new ReportsView(service, orders);
        UI.getCurrent().add(view);

        assertThat(view.from.getValue()).isEqualTo(first);
        // The status rows and a total row of their own.
        assertThat(view.orderStatuses.getListDataView().getItems().map(StatusReportDto.OrderRow::name)).containsExactly("Виконано", "Разом");
        assertThat(view.to.getValue()).isEqualTo(last);
        assertThat(ReportsView.period(week)).isEqualTo("01.10.2026 – 04.10.2026");

        test(view.status).selectItem("Виконано");
        test(view.group).selectItem("за місяцями");
        test(view.show).click();

        verify(service).sales(new ReportFilter(first, last, 5, "MONTH"));
    }
}
