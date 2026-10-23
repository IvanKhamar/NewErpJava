package ua.newerp.web.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.production.ProductionLogFilter;
import ua.newerp.application.production.ProductionModelDto;
import ua.newerp.application.production.ProductionModelFilter;
import ua.newerp.application.production.ProductionModelSummaryDto;
import ua.newerp.application.production.ProductionOperation;
import ua.newerp.application.production.ProductionOperationRequest;
import ua.newerp.application.production.ProductionOperationResult;
import ua.newerp.application.production.ProductionRepository;
import ua.newerp.application.production.ProductionService;
import ua.newerp.application.production.ProductionSizeDto;
import ua.newerp.application.production.SaveProductionModelRequest;
import ua.newerp.application.production.SizeQuantity;
import ua.newerp.application.production.SizeRequest;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.web.auth.CurrentSession;

/** The production pages with mocked services; the models are invented. */
@ViewPackages(classes = {ModelListView.class, ProductionLogView.class})
class ProductionViewsTest extends BrowserlessTest {

    final ProductionRepository repository = mock(ProductionRepository.class);
    final ProductionService service = mock(ProductionService.class);
    final CurrentSession session = mock(CurrentSession.class);
    final ProductionModelDto dress = new ProductionModelDto(4, "Сукня льняна", ProductionModel.CLOTHING, 30, 0,
            List.of(new ProductionSizeDto(41, "S", new BigDecimal("40"), new BigDecimal("6"), new BigDecimal("10"), null),
                    new ProductionSizeDto(42, "M", new BigDecimal("60"), new BigDecimal("4"), new BigDecimal("5"), null)),
            List.of(), List.of(), List.of(), List.of());

    @BeforeEach
    void setUp() {
        when(session.user()).thenReturn(Optional.of(new CurrentUser(3, "taras", "Тарас Мельник", "Storekeeper", false)));
        when(repository.getDto(4)).thenReturn(Optional.of(dress));
        when(repository.listLog(any(ProductionLogFilter.class))).thenReturn(List.of());
    }

    @Test
    void listFiltersModelsThatNeedSewing() {
        var low = new ProductionModelSummaryDto(4, "Сукня льняна", ProductionModel.CLOTHING, 30, BigDecimal.TEN, new BigDecimal("15"),
                new BigDecimal("15"));
        var enough = new ProductionModelSummaryDto(5, "Шарф", ProductionModel.READY_MADE, 5, BigDecimal.ZERO, new BigDecimal("8"),
                BigDecimal.ZERO);
        when(repository.list(any(ProductionModelFilter.class))).thenReturn(List.of(low, enough));
        var view = new ModelListView(repository, service);
        UI.getCurrent().add(view);

        test(view.belowMinimum).click();
        assertThat(view.filtered()).containsExactly(low);
        test(view.belowMinimum).click();
        test(view.type).selectItem("Готовий товар");
        assertThat(view.filtered()).containsExactly(enough);
    }

    @Test
    void sewnOperationSendsOnlySizesWithAQuantityAndShowsWarnings() {
        when(service.record(any(Integer.class), any(), any(), any(Integer.class), any())).thenReturn(
                new ProductionOperationResult(List.of(), List.of("Бракує льону: 1,5 м")));
        var view = new ModelCardView(repository, service, session);
        UI.getCurrent().add(view);
        view.open(4);
        roundTrip();

        var panel = view.operations;
        test(panel.operation).selectItem("Пошито");
        test(panel.quantities.get(42)).setValue(new BigDecimal("3"));
        test(panel.submit).click();

        verify(service).record(4, ProductionOperation.SEWN,
                new ProductionOperationRequest(List.of(new SizeQuantity(42, new BigDecimal("3"))), null), 3, "taras");
        assertThat(view.operations).isNotSameAs(panel); // the card reloaded with fresh stock
        assertThat(view.operations.lastWarnings()).isEmpty();
        assertThat(view.operations.getElement().getTextRecursively()).contains("Бракує льону");
    }

    @Test
    void emptyOperationIsNotSent() {
        var view = new ModelCardView(repository, service, session);
        UI.getCurrent().add(view);
        view.open(4);
        roundTrip();

        test(view.operations.submit).click();

        verify(service, never()).record(any(Integer.class), any(), any(), any(Integer.class), any());
    }

    @Test
    void newModelStartsWithFourSizes() {
        when(service.createModel(any())).thenReturn(9);
        int[] saved = new int[1];
        var dialog = new ModelDialog(service, null, id -> saved[0] = id);
        dialog.open();
        roundTrip();
        test(dialog.name).setValue("Сорочка");
        test(dialog.save).click();

        verify(service).createModel(new SaveProductionModelRequest("Сорочка",
                List.of(size("S"), size("M"), size("L"), size("XL")), ProductionModel.CLOTHING, 30, 0));
        assertThat(saved[0]).isEqualTo(9);
    }

    private static SizeRequest size(String name) {
        return new SizeRequest(name, BigDecimal.ZERO);
    }
}
