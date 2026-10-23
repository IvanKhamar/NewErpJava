package ua.newerp.web.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.theme.lumo.LumoUtility;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.locations.LocationDto;
import ua.newerp.application.locations.LocationService;
import ua.newerp.application.stock.AdjustStockRequest;
import ua.newerp.application.stock.ApplyStocktakingRequest;
import ua.newerp.application.stock.CategoryValueDto;
import ua.newerp.application.stock.ItemConsumptionDto;
import ua.newerp.application.stock.ItemPlacementDto;
import ua.newerp.application.stock.ModelOutputDto;
import ua.newerp.application.stock.PlaceDto;
import ua.newerp.application.stock.SizeOutputDto;
import ua.newerp.application.stock.StockByWarehouseDto;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockItemFilter;
import ua.newerp.application.stock.StockLocationService;
import ua.newerp.application.stock.StockMovementDto;
import ua.newerp.application.stock.StockReportDto;
import ua.newerp.application.stock.StockReportService;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.application.stock.StockService;
import ua.newerp.application.stock.StocktakingCountRequest;
import ua.newerp.application.stock.StocktakingLineDto;
import ua.newerp.application.stock.StocktakingPreviewDto;
import ua.newerp.application.stock.StocktakingResult;
import ua.newerp.application.stock.StocktakingService;
import ua.newerp.application.stock.TransferStockRequest;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;

/** The stock pages with mocked services; the materials are invented. */
@ViewPackages(classes = {StockListView.class, StockItemView.class, StocktakingView.class, StockReportsView.class, StockByWarehouseView.class})
class StockViewsTest extends BrowserlessTest {

    final StockRepository repository = mock(StockRepository.class);
    final StockService service = mock(StockService.class);
    final CurrentSession session = mock(CurrentSession.class);
    final StockLocationService locations = mock(StockLocationService.class);
    final LocationService warehouses = mock(LocationService.class);
    final StockItemDto linen = new StockItemDto(1, "Льон бежевий", "м", 1, "Тканини", null, null, new BigDecimal("4.5"),
            new BigDecimal("10"), true, "Сукні", new BigDecimal("320"));
    final StockItemDto thread = new StockItemDto(2, "Нитки білі", "шт", 2, "Фурнітура", null, null, new BigDecimal("40"),
            new BigDecimal("5"), false, "", BigDecimal.ZERO);

    @BeforeEach
    void setUp() {
        when(repository.listCategories()).thenReturn(List.of(new LookupItem(1, "Тканини"), new LookupItem(2, "Фурнітура")));
        when(repository.listItems(any(StockItemFilter.class))).thenReturn(List.of(linen, thread));
        when(repository.getItemDto(1)).thenReturn(Optional.of(linen));
        when(session.user()).thenReturn(Optional.of(new CurrentUser(3, "taras", "Тарас Мельник", "Storekeeper", false)));
        when(locations.get(1)).thenReturn(new ItemPlacementDto(1, "Льон бежевий", "м", new BigDecimal("4.5"),
                List.of(new PlaceDto(null, StockLocationService.MAIN_NAME, new BigDecimal("4.5")))));
    }

    @Test
    void listFiltersInMemory() {
        var view = new StockListView(repository, service, session);
        UI.getCurrent().add(view);
        assertThat(view.filtered()).hasSize(2);

        test(view.belowMinimum).click();
        assertThat(view.filtered()).containsExactly(linen);
        test(view.belowMinimum).click();
        test(view.search).setValue("нитки");
        assertThat(view.filtered()).containsExactly(thread);
    }

    @Test
    void useIsSavedAsANegativeMovement() {
        when(service.adjust(1, new AdjustStockRequest(new BigDecimal("-1.5"), "Витрата"), 3, "taras")).thenReturn(new StockMovementDto(9, 1,
                new BigDecimal("-1.5"), new BigDecimal("4.5"), new BigDecimal("3"), "Витрата", 3, "taras", LocalDateTime.now()));
        var dialog = new AdjustStockDialog(service, linen, session.user().orElseThrow(), movement -> { });
        dialog.open();
        roundTrip();
        test(dialog.kind).selectItem(AdjustStockDialog.USE);
        test(dialog.quantity).setValue(new BigDecimal("1.5"));
        test(dialog.save).click();

        verify(service).adjust(1, new AdjustStockRequest(new BigDecimal("-1.5"), "Витрата"), 3, "taras");
        assertThat(test(find(Notification.class).single()).getText()).isEqualTo("Льон бежевий: тепер 3 м.");
    }

    @Test
    void zeroQuantityIsNotSent() {
        var dialog = new AdjustStockDialog(service, linen, session.user().orElseThrow(), movement -> { });
        dialog.open();
        roundTrip();
        test(dialog.save).click();

        verify(service, never()).adjust(any(Integer.class), any(AdjustStockRequest.class), any(Integer.class), any(String.class));
    }

    @Test
    void itemCardShowsTheStock() {
        var view = new StockItemView(repository, service, locations, warehouses, session);
        UI.getCurrent().add(view);
        view.open(1);

        assertThat(find(H3.class, view).single().getText()).isEqualTo("4,5 м");
        test(view.minQuantity).setValue(new BigDecimal("8"));
        test(view.saveMinimum).click();
        verify(service).setMinQuantity(1, new BigDecimal("8"));
    }

    @Test
    void itemCardArchivesAfterConfirmation() {
        var view = new StockItemView(repository, service, locations, warehouses, session);
        UI.getCurrent().add(view);
        view.open(1);

        test(view.archive).click();
        test(find(ConfirmDialog.class).single()).confirm();

        verify(service).archiveItem(1);
    }

    @Test
    void stocktakingSendsOnlyCountedRowsAndAnUploadedSheetFillsThem() {
        var stocktaking = mock(StocktakingService.class);
        when(stocktaking.apply(any(ApplyStocktakingRequest.class), any(Integer.class), any(String.class)))
                .thenReturn(new StocktakingResult(1, 1, new BigDecimal("-160.00")));
        var view = new StocktakingView(repository, stocktaking, session);
        UI.getCurrent().add(view);
        roundTrip();

        test(view.apply).click();
        verify(stocktaking, never()).apply(any(ApplyStocktakingRequest.class), any(Integer.class), any(String.class));

        test(view.rows.getFirst().actual).setValue(new BigDecimal("4"));
        assertThat(view.rows.getFirst().difference.getText()).isEqualTo("-0,5");
        test(view.comment).setValue("жовтень");
        test(view.apply).click();
        verify(stocktaking).apply(new ApplyStocktakingRequest(List.of(new StocktakingCountRequest(1, new BigDecimal("4"))), "жовтень"),
                3, "taras");

        byte[] sheet = {1, 2, 3};
        when(stocktaking.preview(sheet)).thenReturn(new StocktakingPreviewDto(
                List.of(new StocktakingLineDto(2, "Нитки білі", "шт", new BigDecimal("40"), new BigDecimal("38"), new BigDecimal("-2"))),
                List.of()));
        view.uploadSheet(sheet);
        assertThat(view.rows.get(1).actual.getValue()).isEqualByComparingTo("38");

        when(stocktaking.preview(sheet)).thenReturn(new StocktakingPreviewDto(List.of(), List.of("Рядок 3: «багато» — не кількість.")));
        view.uploadSheet(sheet);
        assertThat(view.uploadErrors.getElement().getTextRecursively()).contains("«багато»");
    }

    @Test
    void reportsShowValuePaceAndOutputAndReloadForAnotherCover() {
        var reports = mock(StockReportService.class);
        BigDecimal zero = BigDecimal.ZERO;
        var lowLinen = new ItemConsumptionDto(1, "Льон бежевий", "м", new BigDecimal("12"), zero, new BigDecimal("-1"),
                new BigDecimal("0.4"), new BigDecimal("4.5"), zero, 11, new BigDecimal("8"), new BigDecimal("2560"));
        var dress = new ModelOutputDto(1, "Сукня Весна", new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("2"), zero, zero,
                List.of(new SizeOutputDto(1, "S", new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("2"), zero, zero)));
        when(reports.get(any(Integer.class), any(Integer.class))).thenAnswer(a -> new StockReportDto(a.getArgument(0),
                LocalDate.of(2026, 9, 24), a.getArgument(1), List.of(new CategoryValueDto(1, "Тканини", 1, new BigDecimal("1440"))),
                new BigDecimal("1440"), List.of(lowLinen), new BigDecimal("2560"), List.of(dress)));

        var view = new StockReportsView(reports);
        UI.getCurrent().add(view);

        verify(reports).get(30, 30);
        assertThat(find(H3.class, view).all()).extracting(H3::getText)
                .contains("Витрата розхідників з 24.09.2026 · замовити на " + Fmt.money(new BigDecimal("2560")));
        // 11 days left is less than the 30 the order should cover: shown in red
        var daysLeft = (Span) test(view.consumption).getCellComponent(0, 6);
        assertThat(daysLeft.getText()).isEqualTo("11 дн.");
        assertThat(daysLeft.getClassNames()).contains(LumoUtility.TextColor.ERROR);

        view.coverDays.setValue(90);
        verify(reports).get(30, 90);
        assertThat(view.output.getGenericDataView().getItems()).hasSize(1);
    }

    @Test
    void itemCardShowsThePlacesAndMovesAndUsesAtAWarehouse() {
        when(warehouses.list(null)).thenReturn(List.of(new LocationDto(901, "Цех", "", "", "", "", "", ""),
                new LocationDto(902, "Б-склад", "", "", "", "", "", "")));
        when(locations.get(1)).thenReturn(new ItemPlacementDto(1, "Льон бежевий", "м", new BigDecimal("4.5"),
                List.of(new PlaceDto(null, StockLocationService.MAIN_NAME, new BigDecimal("3")), new PlaceDto(901, "Цех", new BigDecimal("1.5")))));
        when(locations.adjust(any(Integer.class), any(Integer.class), any(AdjustStockRequest.class), any(Integer.class), any(String.class)))
                .thenReturn(new StockMovementDto(9, 1, new BigDecimal("-1"), new BigDecimal("4.5"), new BigDecimal("3.5"), "Крій · Цех", 3,
                        "taras", LocalDateTime.now()));
        var view = new StockItemView(repository, service, locations, warehouses, session);
        UI.getCurrent().add(view);
        view.open(1);

        // the main stock first, then every warehouse by name, also one without this material
        assertThat(view.places).extracting(PlaceDto::name).containsExactly(StockLocationService.MAIN_NAME, "Б-склад", "Цех");
        assertThat(view.transfer.isVisible()).isTrue();

        test(view.transfer).click();
        var move = find(TransferStockDialog.class).single();
        move.to.setValue(view.places.get(2));
        test(move.quantity).setValue(new BigDecimal("2"));
        test(move.save).click();
        verify(locations).transfer(1, new TransferStockRequest(null, 901, new BigDecimal("2"), ""), 3, "taras");

        test(view.adjust).click();
        var use = find(AdjustStockDialog.class).single();
        test(use.kind).selectItem(AdjustStockDialog.USE);
        use.place.setValue(view.places.get(2));
        test(use.quantity).setValue(BigDecimal.ONE);
        test(use.reason).setValue("Крій");
        test(use.save).click();
        verify(locations).adjust(1, 901, new AdjustStockRequest(new BigDecimal("-1"), "Крій"), 3, "taras");
        verify(service, never()).adjust(any(Integer.class), any(AdjustStockRequest.class), any(Integer.class), any(String.class));
    }

    @Test
    void withoutWarehousesThereIsNothingToMove() {
        var view = new StockItemView(repository, service, locations, warehouses, session);
        UI.getCurrent().add(view);
        view.open(1);

        assertThat(view.transfer.isVisible()).isFalse();
        assertThat(view.places).hasSize(1);
    }

    @Test
    void overviewHasAColumnPerPlace() {
        when(locations.byWarehouse()).thenReturn(new StockByWarehouseDto(
                List.of(new StockByWarehouseDto.Place(null, StockLocationService.MAIN_NAME, new BigDecimal("960")),
                        new StockByWarehouseDto.Place(901, "Цех", new BigDecimal("480"))),
                List.of(new StockByWarehouseDto.Row(1, "Льон бежевий", "м", "Тканини", new BigDecimal("4.5"),
                        List.of(new BigDecimal("3"), new BigDecimal("1.5"))))));
        var view = new StockByWarehouseView(locations);
        UI.getCurrent().add(view);

        assertThat(view.grid.getColumns()).hasSize(5);
        assertThat(test(view.grid).getCellText(0, 4)).isEqualTo("1,5");
    }
}
