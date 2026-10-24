package ua.newerp.web.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import ua.newerp.application.catalog.UpdateProductRequest;
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
        when(service.labels()).thenReturn(List.of(hit, new LookupItem(2, "Новинка")));
        when(service.categories()).thenReturn(List.of(dresses));
        var view = new ProductCardView(service);
        UI.getCurrent().add(view);

        view.open(70);
        assertThat(test(view.sizes).size()).isEqualTo(2);
        assertThat(view.getPageTitle()).isEqualTo("Сукня лляна — NewErp");
        assertThat(view.price.getValue()).isEqualByComparingTo("1500");
        assertThat(view.labels.getValue()).containsExactly(hit);
        assertThat(view.categories.getValue()).containsExactly(dresses);

        view.open(999);
        assertThat(view.getChildren().filter(Paragraph.class::isInstance).map(c -> ((Paragraph) c).getText()))
                .containsExactly("Товар №999 не знайдено.");
    }

    @Test
    void saveSendsTheFormWithTheVersionTheCardWasOpenedWith() {
        LocalDateTime opened = LocalDateTime.of(2026, 10, 1, 10, 0);
        var card = new ProductCardDto(70, "Сукня лляна", "SL-01", new BigDecimal("1500"), true, 6, null, opened, List.of(), List.of(hit),
                List.of(dresses));
        when(service.get(70)).thenReturn(card);
        when(service.labels()).thenReturn(List.of(hit));
        when(service.categories()).thenReturn(List.of(dresses));
        when(service.update(eq(70), any())).thenReturn(card);
        var view = new ProductCardView(service);
        UI.getCurrent().add(view);
        view.open(70);

        view.price.setValue(new BigDecimal("1399.50"));
        view.enabled.setValue(false);
        view.labels.clear();
        test(view.save).click();

        verify(service).update(70, new UpdateProductRequest(new BigDecimal("1399.50"), false, List.of(), List.of(30), opened));
    }
}
