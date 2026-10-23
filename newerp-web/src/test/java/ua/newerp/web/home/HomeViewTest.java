package ua.newerp.web.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.reports.DashboardDto;
import ua.newerp.application.reports.DashboardTile;
import ua.newerp.application.reports.ProductSalesRow;
import ua.newerp.application.reports.ReportService;
import ua.newerp.application.reports.SalesReportRow;
import ua.newerp.web.Access;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.finance.BankPaymentsView;
import ua.newerp.web.finance.CashboxView;
import ua.newerp.web.orders.OrderListView;
import ua.newerp.web.production.ModelListView;
import ua.newerp.web.reports.ReportsView;
import ua.newerp.web.stock.StockListView;

/** The home page with a mocked report service; the figures are invented. */
@ViewPackages(classes = {OrderListView.class, StockListView.class, ModelListView.class, CashboxView.class,
        BankPaymentsView.class, ReportsView.class})
class HomeViewTest extends BrowserlessTest {

    final CurrentSession session = mock(CurrentSession.class);
    final ReportService reports = mock(ReportService.class);

    @BeforeEach
    void setUp() {
        when(session.user()).thenReturn(Optional.of(new CurrentUser(7, "olena", "Олена Коваль", "Manager", false)));
        when(session.hasAnyRole(Access.ORDERS)).thenReturn(true); // FINANCE has the same roles, so it matches too
    }

    @Test
    void managerSeesTheSalesSummary() {
        var tile = new DashboardTile(new BigDecimal("12"), new BigDecimal("10"), 20);
        var day = new SalesReportRow(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 22), 2, 3, new BigDecimal("2500"));
        when(reports.dashboard()).thenReturn(new DashboardDto(tile, tile, tile, tile, tile, tile, List.of(day),
                List.of(new ProductSalesRow(1, "Сукня льняна", "SL-1", 4, new BigDecimal("6000")))));
        var view = new HomeView(session, reports);
        UI.getCurrent().add(view);

        assertThat(find(DashboardPanel.class).exists()).isTrue();
        assertThat(view.getElement().getTextRecursively()).contains("+20% проти вчора", "Сукня льняна");
    }

    @Test
    void summaryErrorDoesNotBreakThePage() {
        when(reports.dashboard()).thenThrow(new IllegalStateException("база недоступна"));
        var view = new HomeView(session, reports);
        UI.getCurrent().add(view);

        assertThat(view.getElement().getTextRecursively()).contains("Зведення продажів зараз недоступне", "Замовлення");
    }
}
