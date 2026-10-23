package ua.newerp.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.catalog.PreOrderFilter;
import ua.newerp.application.catalog.PreOrderRow;
import ua.newerp.application.catalog.PreOrderService;
import ua.newerp.application.catalog.PreOrderSettleResult;
import ua.newerp.application.catalog.UpdatePreOrderRequest;

/** The pre-orders page with a mocked service; products are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class PreOrdersViewTest extends BrowserlessTest {

    final PreOrderService service = mock(PreOrderService.class);
    final PreOrderRow sizeM = new PreOrderRow(51, 301, "Сукня «Липа»", "LP-01", "M", -2, 5, null, 1);

    @Test
    void saveTurnsOnAfterAChangeAndSendsTheRow() {
        when(service.list(new PreOrderFilter("", false))).thenReturn(List.of(sizeM));
        var view = new PreOrdersView(service);
        UI.getCurrent().add(view);
        roundTrip();
        var row = view.grid.getListDataView().getItem(0);
        assertThat(row.save.isEnabled()).isFalse();

        test(row.quota).setValue(8);
        row.date.setValue(LocalDate.of(2026, 11, 15));
        test(row.save).click();

        verify(service).update(51, new UpdatePreOrderRequest(8, LocalDate.of(2026, 11, 15)));
    }

    @Test
    void settleReportsHowManySizesChanged() {
        when(service.list(new PreOrderFilter("", false))).thenReturn(List.of(sizeM));
        when(service.settle()).thenReturn(new PreOrderSettleResult(1));
        var view = new PreOrdersView(service);
        UI.getCurrent().add(view);

        test(view.settle).click();

        verify(service).settle();
    }
}
