package ua.newerp.web.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationService;
import ua.newerp.application.production.ComponentCountRequest;
import ua.newerp.application.production.ProductionLocationService;
import ua.newerp.application.production.SewnTransferRequest;
import ua.newerp.application.production.SizePlacesDto;
import ua.newerp.application.stock.PlaceDto;
import ua.newerp.application.stock.StockLocationService;
import ua.newerp.application.production.ComponentDto;
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
import ua.newerp.application.production.CuttingPlanDto;
import ua.newerp.application.production.CuttingPlanItemDto;
import ua.newerp.application.production.CuttingPlanModelDto;
import ua.newerp.application.production.CuttingPlanService;
import ua.newerp.application.production.CuttingPlanSizeDto;
import ua.newerp.application.production.ProductionStocktakingRequest;
import ua.newerp.application.production.ProductionStocktakingResult;
import ua.newerp.application.production.SizeCountRequest;
import ua.newerp.application.production.SizeQuantity;
import ua.newerp.application.production.SetProductionLinksRequest;
import ua.newerp.application.production.SetSewnBomRequest;
import ua.newerp.application.production.SewingPlanDto;
import ua.newerp.application.production.SewingPlanRowDto;
import ua.newerp.application.production.SewingPlanService;
import ua.newerp.application.production.SewnBomRowRequest;
import ua.newerp.application.production.SizeOptionRequest;
import ua.newerp.application.production.SizeRequest;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.domain.production.ProductionModel;
import ua.newerp.web.auth.CurrentSession;

/** The production pages with mocked services; the models are invented. */
@ViewPackages(classes = {ModelListView.class, ProductionLogView.class, SewingPlanView.class, CuttingPlanView.class})
class ProductionViewsTest extends BrowserlessTest {

    final ProductionRepository repository = mock(ProductionRepository.class);
    final ProductionService service = mock(ProductionService.class);
    final StockRepository stock = mock(StockRepository.class);
    final CurrentSession session = mock(CurrentSession.class);
    final ProductionLocationService locations = mock(ProductionLocationService.class);
    final LocationService warehouses = mock(LocationService.class);
    final ProductionModelDto dress = new ProductionModelDto(4, "Сукня льняна", ProductionModel.CLOTHING, 30, 0,
            List.of(new ProductionSizeDto(41, "S", new BigDecimal("40"), new BigDecimal("6"), new BigDecimal("10"), null),
                    new ProductionSizeDto(42, "M", new BigDecimal("60"), new BigDecimal("4"), new BigDecimal("5"), null)),
            List.of(), List.of(), List.of(new ComponentDto(7, "Перед", 1, Map.of(41, BigDecimal.TWO), List.of())), List.of());
    final StockItemDto linen = new StockItemDto(1, "Льон бежевий", "м", 1, "Тканини", null, null, BigDecimal.TEN, BigDecimal.ONE, false,
            "", BigDecimal.ZERO);

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
        var view = new ModelCardView(repository, service, stock, locations, warehouses, session);
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
        var view = new ModelCardView(repository, service, stock, locations, warehouses, session);
        UI.getCurrent().add(view);
        view.open(4);
        roundTrip();

        test(view.operations.submit).click();

        verify(service, never()).record(any(Integer.class), any(), any(), any(Integer.class), any());
    }

    @Test
    void stocktakingSendsOnlyTheCountsThatDifferFromTheBooks() {
        when(service.recount(any(Integer.class), any(ProductionStocktakingRequest.class), any(Integer.class), any(String.class)))
                .thenReturn(new ProductionStocktakingResult(List.of(), List.of(), 1));
        var view = new ModelCardView(repository, service, stock, locations, warehouses, session);
        UI.getCurrent().add(view);
        view.open(4);
        roundTrip();
        test(view.stocktaking).click();
        roundTrip();
        var dialog = find(ModelStocktakingDialog.class).single();

        test(dialog.save).click();
        verify(service, never()).recount(any(Integer.class), any(ProductionStocktakingRequest.class), any(Integer.class), any(String.class));

        test(dialog.sewn.get(41)).setValue(new BigDecimal("9"));
        test(dialog.components.get(List.of(7, 42))).setValue(BigDecimal.ONE);
        test(dialog.save).click();

        verify(service).recount(4, new ProductionStocktakingRequest(List.of(new SizeCountRequest(41, null, new BigDecimal("9"))),
                List.of(new ComponentCountRequest(7, 42, BigDecimal.ONE)), ""), 3, "taras");
    }

    /** A workshop warehouse "Цех" (901) holds 4 of the 10 sewn S. */
    private ModelCardView cardWithAWarehouse() {
        when(warehouses.list(null)).thenReturn(List.of(new LocationDto(901, "Цех", "", "", "", "", "", "")));
        when(locations.get(4)).thenReturn(List.of(
                new SizePlacesDto(41, "S", new BigDecimal("10"), List.of(new PlaceDto(null, StockLocationService.MAIN_NAME,
                        new BigDecimal("6")), new PlaceDto(901, "Цех", new BigDecimal("4")))),
                new SizePlacesDto(42, "M", new BigDecimal("5"), List.of(new PlaceDto(null, StockLocationService.MAIN_NAME,
                        new BigDecimal("5"))))));
        var view = new ModelCardView(repository, service, stock, locations, warehouses, session);
        UI.getCurrent().add(view);
        view.open(4);
        roundTrip();
        return view;
    }

    @Test
    void sewnPiecesCanComeToAWarehouse() {
        when(service.record(any(Integer.class), any(), any(), any(Integer.class), any())).thenReturn(
                new ProductionOperationResult(List.of(), List.of()));
        var panel = cardWithAWarehouse().operations;
        assertThat(panel.place.isVisible()).isFalse(); // cutting is not placed
        test(panel.operation).selectItem("Пошито");
        test(panel.place).selectItem("Цех");
        test(panel.quantities.get(41)).setValue(new BigDecimal("2"));
        test(panel.submit).click();

        verify(service).record(4, ProductionOperation.SEWN,
                new ProductionOperationRequest(List.of(new SizeQuantity(41, new BigDecimal("2"))), null, 901), 3, "taras");
    }

    @Test
    void sewnPiecesMoveBetweenWarehouses() {
        var view = cardWithAWarehouse();
        view.tabs.setSelectedIndex(1);
        var panel = find(SewnPlacesPanel.class).single();
        assertThat(test(panel.grid).getCellText(0, 3)).isEqualTo("4");

        test(panel.from).selectItem("Цех");
        test(panel.to).selectItem(StockLocationService.MAIN_NAME);
        test(panel.quantity).setValue(new BigDecimal("3"));
        test(panel.move).click();

        verify(locations).transfer(4, new SewnTransferRequest(41, 901, null, new BigDecimal("3"), null), 3, "taras");
    }

    @Test
    void stocktakingAtAWarehouseCountsOnlyTheSewnPiecesThere() {
        when(service.recount(any(Integer.class), any(ProductionStocktakingRequest.class), any(Integer.class), any(String.class)))
                .thenReturn(new ProductionStocktakingResult(List.of(), List.of(), 0));
        var view = cardWithAWarehouse();
        test(view.stocktaking).click();
        roundTrip();
        var dialog = find(ModelStocktakingDialog.class).single();
        assertThat(dialog.sewn.get(41).getValue()).isEqualByComparingTo("6"); // the main stock: 10 sewn, 4 at the workshop

        test(dialog.place).selectItem("Цех");
        assertThat(dialog.sewn.get(41).getValue()).isEqualByComparingTo("4");
        test(dialog.sewn.get(41)).setValue(new BigDecimal("3"));
        test(dialog.save).click();

        verify(service).recount(4, new ProductionStocktakingRequest(List.of(new SizeCountRequest(41, null, new BigDecimal("3"))),
                List.of(), "", 901), 3, "taras");
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

    @Test
    void sewnBomMixesComponentsAndConsumables() {
        var dialog = new BomDialog(service, dress, null, List.of(linen), () -> { });
        dialog.open();
        roundTrip();
        assertThat(dialog.choices()).extracting(BomDialog.Choice::label).containsExactly("Деталь: Перед", "Льон бежевий (м)");

        dialog.addRow(null, dialog.choices().get(0), BigDecimal.ONE);
        dialog.addRow(42, dialog.choices().get(1), new BigDecimal("0.2"));
        test(dialog.save).click();

        verify(service).setSewnBom(4, new SetSewnBomRequest(List.of(
                new SewnBomRowRequest(null, 7, null, BigDecimal.ONE),
                new SewnBomRowRequest(42, null, 1, new BigDecimal("0.2")))));
    }

    @Test
    void linksRejectAProductIdThatIsNotANumber() {
        var links = new LinksPanel(service, dress, () -> { });
        UI.getCurrent().add(links);

        test(links.productIds).setValue("101, abc");
        test(links.save).click();
        verify(service, never()).setLinks(any(Integer.class), any());

        test(links.productIds).setValue("101; 102 101");
        test(links.options.get(41)).setValue(55);
        test(links.save).click();
        verify(service).setLinks(4, new SetProductionLinksRequest(List.of(101, 102), List.of(),
                List.of(new SizeOptionRequest(41, 55), new SizeOptionRequest(42, null))));
    }

    @Test
    void sewingPlanReloadsWhenTheFilterChanges() {
        var plans = mock(SewingPlanService.class);
        var row = new SewingPlanRowDto(4, "Сукня льняна", 41, "S", BigDecimal.TWO, BigDecimal.TWO, BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.TWO, BigDecimal.ONE, BigDecimal.ONE, LocalDate.of(2026, 10, 30), false, List.of(1001));
        when(plans.get(true)).thenReturn(new SewingPlanDto(1, List.of(), List.of()));
        when(plans.get(false)).thenReturn(new SewingPlanDto(1, List.of(row), List.of()));
        var view = new SewingPlanView(plans);
        UI.getCurrent().add(view);
        assertThat(view.plan.rows()).isEmpty();

        test(view.onlyToSew).click();

        assertThat(view.plan.rows()).containsExactly(row);
    }

    @Test
    void cuttingPlanShowsSizesAndWarnsAboutMissingConsumables() {
        var plans = mock(CuttingPlanService.class);
        var model = new CuttingPlanModelDto(4, "Сукня льняна", 30, new BigDecimal("7"), List.of(
                new CuttingPlanSizeDto(41, "S", new BigDecimal("40"), 12, BigDecimal.ONE, new BigDecimal("6"), BigDecimal.ONE, new BigDecimal("6")),
                new CuttingPlanSizeDto(42, "M", new BigDecimal("60"), 18, BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal("7"), BigDecimal.ONE)));
        var fabric = new CuttingPlanItemDto(1, "Льон бежевий", "м", new BigDecimal("14"), BigDecimal.TEN, new BigDecimal("4"),
                List.of("Сукня льняна"));
        when(plans.get(true)).thenReturn(new CuttingPlanDto(List.of(model), List.of(fabric)));
        when(plans.get(false)).thenReturn(new CuttingPlanDto(List.of(), List.of()));
        var view = new CuttingPlanView(plans);
        UI.getCurrent().add(view);

        assertThat(test(view.sizes).size()).isEqualTo(2);
        assertThat(view.shortage.isVisible()).isTrue();
        assertThat(view.shortage.getText()).contains("не вистачить розхідників: 1");

        test(view.onlyToCut).click();

        assertThat(view.plan.models()).isEmpty();
        assertThat(view.sizes.isVisible()).isFalse();
        assertThat(view.shortage.isVisible()).isFalse();
    }

    private static SizeRequest size(String name) {
        return new SizeRequest(name, BigDecimal.ZERO);
    }
}
