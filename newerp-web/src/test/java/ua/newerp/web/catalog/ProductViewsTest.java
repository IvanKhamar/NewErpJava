package ua.newerp.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Paragraph;

import ua.newerp.application.catalog.ProductCardDto;
import ua.newerp.application.catalog.ProductFilter;
import ua.newerp.application.catalog.ProductListItem;
import ua.newerp.application.catalog.ProductService;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.common.PagedResult;

/** The product list and card with a mocked service; the products are invented. */
@ViewPackages(classes = {ProductListView.class, ProductCardView.class})
class ProductViewsTest extends BrowserlessTest {

    final ProductService service = mock(ProductService.class);
    final LookupItem dresses = new LookupItem(30, "Сукні");
    final LookupItem hit = new LookupItem(1, "Хіт");

    @Test
    void listFiltersReachTheService() {
        when(service.categories()).thenReturn(List.of(dresses));
        when(service.labels()).thenReturn(List.of(hit));
        var dress = new ProductListItem(70, "Сукня лляна", "SL-01", new BigDecimal("1500"), true, 6, null);
        when(service.list(any())).thenReturn(PagedResult.of(List.of(dress), 1, ProductListView.PAGE_SIZE, 1));
        var view = new ProductListView(service);
        UI.getCurrent().add(view);

        assertThat(test(view.grid).size()).isEqualTo(1);
        test(view.category).selectItem("Сукні");
        test(view.label).selectItem("Хіт");
        test(view.enabled).selectItem("Увімкнені");
        test(view.search).setValue("лляна");

        // Each filter change reloads the grid; the last reload carries all four filters.
        verify(service).list(new ProductFilter("лляна", 30, 1, true, 1, ProductListView.PAGE_SIZE));
    }

    @Test
    void cardShowsSizesAndNamesOrNotFound() {
        var card = new ProductCardDto(70, "Сукня лляна", "SL-01", new BigDecimal("1500"), true, 6, null, LocalDateTime.of(2026, 10, 1, 10, 0),
                List.of(new ProductCardDto.Size(102, "M", 4), new ProductCardDto.Size(103, "L", 2)), List.of(hit), List.of(dresses));
        when(service.get(70)).thenReturn(card);
        when(service.get(999)).thenThrow(new NotFoundException("Товар 999 не знайдено."));
        var view = new ProductCardView(service);
        UI.getCurrent().add(view);

        view.open(70);
        assertThat(test(view.sizes).size()).isEqualTo(2);
        assertThat(view.getPageTitle()).isEqualTo("Сукня лляна — NewErp");
        assertThat(ProductCardView.names(card.labels())).isEqualTo("Хіт");
        assertThat(ProductCardView.names(List.of())).isEqualTo("—");

        view.open(999);
        assertThat(view.getChildren().filter(Paragraph.class::isInstance).map(c -> ((Paragraph) c).getText()))
                .containsExactly("Товар №999 не знайдено.");
    }
}
