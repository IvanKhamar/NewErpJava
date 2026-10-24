package ua.newerp.web.competitors;

import static org.assertj.core.api.Assertions.assertThat;
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

import ua.newerp.application.catalog.ProductSortService;
import ua.newerp.application.catalog.SortProductDto;
import ua.newerp.application.competitors.AddCompetitorLinkRequest;
import ua.newerp.application.competitors.CompetitorCheckResult;
import ua.newerp.application.competitors.CompetitorDto;
import ua.newerp.application.competitors.CompetitorLinkDto;
import ua.newerp.application.competitors.CompetitorPriceRequest;
import ua.newerp.application.competitors.CompetitorReportDto;
import ua.newerp.application.competitors.CompetitorService;
import ua.newerp.application.competitors.SaveCompetitorRequest;

/** The competitor prices page with a mocked service; competitors and dresses are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class CompetitorPricesViewTest extends BrowserlessTest {

    final CompetitorService service = mock(CompetitorService.class);
    final ProductSortService products = mock(ProductSortService.class);
    final CompetitorDto moda = new CompetitorDto(1, "Мода", "https://moda.example", "", true, 1);
    final CompetitorLinkDto page = new CompetitorLinkDto(7, 1, "Мода", 201, "Сукня 201", "https://moda.example/201",
            new BigDecimal("950.00"), LocalDateTime.of(2026, 10, 24, 7, 0), "");

    @Test
    void comparesPricesAddsPagesAndCompetitors() {
        when(service.competitors()).thenReturn(List.of(moda));
        when(service.links(null)).thenReturn(List.of(page));
        when(service.report()).thenReturn(new CompetitorReportDto(List.of(new CompetitorReportDto.Column(1, "Мода")),
                List.of(new CompetitorReportDto.Row(201, "Сукня 201", new BigDecimal("1000"), List.of(new BigDecimal("950.00")),
                        new BigDecimal("950.00"), new BigDecimal("5.3")))));
        when(service.checkAll()).thenReturn(new CompetitorCheckResult(1, 0, 0));
        when(service.addLink(new AddCompetitorLinkRequest(1, 202, "https://moda.example/202")))
                .thenReturn(new CompetitorLinkDto(8, 1, "Мода", 202, "Сукня 202", "https://moda.example/202", null, null, ""));
        when(products.searchProducts("Сукня")).thenReturn(List.of(new SortProductDto(202, "Сукня 202", "", 0)));
        var view = new CompetitorPricesView(service, products);
        UI.getCurrent().add(view);
        roundTrip();

        assertThat(test(view.report).getCellText(0, 0)).isEqualTo("Сукня 201");
        assertThat(view.report.getColumns()).hasSize(4);
        assertThat(CompetitorPricesView.difference(view.data.rows().getFirst()).getElement().getText()).isEqualTo("+5,3%");
        test(view.checkAll).click();
        verify(service).checkAll();

        view.tabs.setSelectedIndex(1);
        roundTrip();
        assertThat(test(view.links).getCellText(0, 0)).isEqualTo("Сукня 201");
        test(view.competitor).selectItem("Мода");
        view.product.setValue(new SortProductDto(202, "Сукня 202", "", 0));
        test(view.url).setValue("https://moda.example/202");
        test(view.addLink).click();
        verify(service).check(8);

        var dialog = new CompetitorDialog(service, null, view::load);
        dialog.open();
        roundTrip();
        test(dialog.name).setValue("Сукні-Плюс");
        test(dialog.site).setValue("https://dress-plus.example");
        test(dialog.save).click();
        verify(service).createCompetitor(new SaveCompetitorRequest("Сукні-Плюс", "https://dress-plus.example", "", true));

        var prices = new LinkDialog(service, page, view::load);
        prices.open();
        roundTrip();
        test(prices.price).setValue(new BigDecimal("990"));
        test(prices.save).click();
        verify(service).setPrice(7, new CompetitorPriceRequest(new BigDecimal("990")));
    }
}
