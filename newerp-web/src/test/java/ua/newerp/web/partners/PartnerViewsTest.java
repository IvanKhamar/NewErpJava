package ua.newerp.web.partners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.partners.CategoryMapRow;
import ua.newerp.application.partners.PartnerCategoryMapDto;
import ua.newerp.application.partners.PartnerDto;
import ua.newerp.application.partners.PartnerListItemDto;
import ua.newerp.application.partners.PartnerProductPickDto;
import ua.newerp.application.partners.PartnerService;
import ua.newerp.application.partners.ProductMapRow;
import ua.newerp.application.partners.ProductSizePriceDto;
import ua.newerp.application.partners.SavePartnerRequest;
import ua.newerp.application.partners.ShopCategoryDto;

/** The partner pages with a mocked service; partners and products are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class PartnerViewsTest extends BrowserlessTest {

    final PartnerService service = mock(PartnerService.class);
    final ShopCategoryDto dresses = new ShopCategoryDto(20, "Сукні", List.of("S", "M"));

    @Test
    void listOpensThePartnerCard() {
        when(service.list()).thenReturn(List.of(new PartnerListItemDto(7, "shafa", "Шафа", "Vidro", true, true, 1, 0)));
        var view = new PartnerListView(service);
        UI.getCurrent().add(view);
        roundTrip();

        test(view.grid).clickRow(0);

        assertThat(UI.getCurrent().getActiveViewLocation().getPath()).isEqualTo("partners/7");
    }

    @Test
    void savesTheMappingAsEdited() {
        when(service.listCategories()).thenReturn(List.of(dresses));
        when(service.get(7)).thenReturn(Optional.of(partner()));
        var view = new PartnerCardView(service);
        UI.getCurrent().add(view);
        view.open(7);
        roundTrip();

        var row = view.categoryRows.getFirst();
        assertThat(row.size.getValue()).isEqualTo("M");
        row.percentSupply.setValue(new BigDecimal("15"));
        view.addProduct(new PartnerProductPickDto(301, "Сукня «Липа»", true, List.of(new ProductSizePriceDto("S", new BigDecimal("1200")))));
        view.productRows.getFirst().priceSupply.setValue(new BigDecimal("990"));
        test(view.save).click();

        verify(service).update(7, new SavePartnerRequest("shafa", "Шафа", true, "", true, "Vidro", "", 5,
                List.of(new CategoryMapRow(20, "M", "11", "", new BigDecimal("15"), null)),
                List.of(new ProductMapRow(301, "", "", "", new BigDecimal("990"), null))));
    }

    @Test
    void createsANewPartnerAndOpensIt() {
        when(service.listCategories()).thenReturn(List.of(dresses));
        var view = new PartnerCardView(service);
        UI.getCurrent().add(view);
        view.open(null);
        assertThat(view.delete.isVisible()).isFalse();
        test(view.name).setValue("Каста");
        test(view.code).setValue("kasta");
        when(service.create(new SavePartnerRequest("kasta", "Каста", true, "", false, "Vidro", "", 5, List.of(), List.of()))).thenReturn(8);

        test(view.save).click();

        assertThat(UI.getCurrent().getActiveViewLocation().getPath()).isEqualTo("partners/8");
    }

    private static PartnerDto partner() {
        return new PartnerDto(7, "shafa", "Шафа", true, "", true, "Vidro", "", 5, LocalDateTime.of(2026, 10, 1, 9, 0),
                List.of(new PartnerCategoryMapDto(1, 20, "Сукні", "M", "11", "", null, null)), List.of());
    }
}
