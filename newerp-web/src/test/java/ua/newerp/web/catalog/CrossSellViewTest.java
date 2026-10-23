package ua.newerp.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

import ua.newerp.application.catalog.CrossSellItemDto;
import ua.newerp.application.catalog.CrossSellService;
import ua.newerp.application.catalog.ProductSortService;
import ua.newerp.application.catalog.SaveCrossSellItemRequest;
import ua.newerp.application.common.LookupItem;
import ua.newerp.domain.catalog.CrossSellItem;

/** The cross-sell panel page with mocked services; the categories and products are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class CrossSellViewTest extends BrowserlessTest {

    final CrossSellService service = mock(CrossSellService.class);
    final ProductSortService products = mock(ProductSortService.class);
    final CrossSellItemDto belts = new CrossSellItemDto(5, CrossSellItem.CATEGORY, 30, "Пояси", new BigDecimal("10.00"), 4);

    @Test
    void addsACategoryAndRemovesALine() {
        when(service.categories()).thenReturn(List.of(new LookupItem(30, "Пояси")));
        when(service.list()).thenReturn(List.of(belts));
        var view = new CrossSellView(service, products);
        UI.getCurrent().add(view);

        assertThat(test(view.grid).getCellText(0, 1)).isEqualTo("Пояси");
        assertThat(test(view.grid).getCellText(0, 2)).isEqualTo("4");
        assertThat(view.product.isVisible()).isFalse();

        test(view.category).selectItem("Пояси");
        test(view.discount).setValue(new BigDecimal("10"));
        test(view.add).click();
        verify(service).add(new SaveCrossSellItemRequest(CrossSellItem.CATEGORY, 30, new BigDecimal("10")));

        view.remove(belts);
        verify(service).delete(5);
        view.setDiscount(belts, new BigDecimal("12.5"));
        verify(service).setDiscount(5, new BigDecimal("12.5"));
        assertThat(find(ConfirmDialog.class).all()).isEmpty();
    }
}
