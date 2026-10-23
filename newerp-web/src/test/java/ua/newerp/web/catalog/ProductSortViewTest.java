package ua.newerp.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.catalog.ApplySortRequest;
import ua.newerp.application.catalog.CrossSellMetricDto;
import ua.newerp.application.catalog.ProductSortService;
import ua.newerp.application.catalog.SalesPeriod;
import ua.newerp.application.catalog.SetPinRequest;
import ua.newerp.application.catalog.SortGroupDto;
import ua.newerp.application.catalog.SortPinDto;
import ua.newerp.application.catalog.SortPreviewRow;
import ua.newerp.application.catalog.SortProductDto;
import ua.newerp.application.catalog.SortResult;
import ua.newerp.application.catalog.SortSnapshotDto;
import ua.newerp.domain.catalog.SortGroupType;

/** The product order page with a mocked service; products are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class ProductSortViewTest extends BrowserlessTest {

    final ProductSortService service = mock(ProductSortService.class);
    final SortGroupDto dresses = new SortGroupDto(SortGroupType.CATEGORY.ordinal(), "20", "Сукні");
    final SortGroupDto panel = new SortGroupDto(SortGroupType.CROSS_SELL.ordinal(), "panel", "Панель допродажу");
    final SalesPeriod days90 = new SalesPeriod(90, null, null);

    @BeforeEach
    void setUp() {
        when(service.listGroups()).thenReturn(List.of(dresses, panel));
        when(service.getCrossSellMetric()).thenReturn(new CrossSellMetricDto(false));
        when(service.listPins()).thenReturn(List.of(new SortPinDto(5, SortGroupType.CATEGORY.ordinal(), "20", 1, 301, "Сукня «Липа»")));
        when(service.listSnapshots()).thenReturn(List.of(new SortSnapshotDto(9, "Перерахунок усіх груп", LocalDateTime.of(2026, 10, 20, 9, 0))));
        when(service.preview(SortGroupType.CATEGORY, "20", days90)).thenReturn(List.of(
                new SortPreviewRow(301, "Сукня «Липа»", "", 12, true, 2, 1),
                new SortPreviewRow(302, "Сукня «Верба»", "", 30, false, 1, 2)));
    }

    @Test
    void movedRowsAreWrittenInTheNewOrder() {
        when(service.apply(new ApplySortRequest(SortGroupType.CATEGORY.ordinal(), "20", List.of(302, 301)))).thenReturn(new SortResult(10, 2));
        var view = new ProductSortView(service);
        UI.getCurrent().add(view);

        test(view.group).selectItem("Сукні");
        assertThat(view.panelOnly.isVisible()).isFalse();
        view.move(0, 1);
        test(view.apply).click();

        verify(service).apply(new ApplySortRequest(SortGroupType.CATEGORY.ordinal(), "20", List.of(302, 301)));
    }

    @Test
    void pinsAProductInTheChosenGroup() {
        var view = new ProductSortView(service);
        UI.getCurrent().add(view);
        test(view.group).selectItem("Сукні");
        test(view.pinPosition).setValue(3);
        view.pinProduct.setItems(new SortProductDto(302, "Сукня «Верба»", "", 0));
        view.pinProduct.setValue(new SortProductDto(302, "Сукня «Верба»", "", 0));

        test(view.pin).click();

        verify(service).setPin(new SetPinRequest(SortGroupType.CATEGORY.ordinal(), "20", 3, 302));
    }

    @Test
    void crossSellShowsTheMetricSwitch() {
        when(service.preview(SortGroupType.CROSS_SELL, "panel", days90)).thenReturn(List.of());
        var view = new ProductSortView(service);
        UI.getCurrent().add(view);

        test(view.group).selectItem("Панель допродажу");

        assertThat(view.panelOnly.isVisible()).isTrue();
        assertThat(view.apply.isEnabled()).isFalse();
    }
}
