package ua.newerp.web.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import com.vaadin.flow.component.notification.Notification;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.stock.AdjustStockRequest;
import ua.newerp.application.stock.ApplyStocktakingRequest;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockItemFilter;
import ua.newerp.application.stock.StockMovementDto;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.application.stock.StockService;
import ua.newerp.application.stock.StocktakingCountRequest;
import ua.newerp.application.stock.StocktakingLineDto;
import ua.newerp.application.stock.StocktakingPreviewDto;
import ua.newerp.application.stock.StocktakingResult;
import ua.newerp.application.stock.StocktakingService;
import ua.newerp.web.auth.CurrentSession;

/** The stock pages with mocked services; the materials are invented. */
@ViewPackages(classes = {StockListView.class, StockItemView.class, StocktakingView.class})
class StockViewsTest extends BrowserlessTest {

    final StockRepository repository = mock(StockRepository.class);
    final StockService service = mock(StockService.class);
    final CurrentSession session = mock(CurrentSession.class);
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
        var view = new StockItemView(repository, service, session);
        UI.getCurrent().add(view);
        view.open(1);

        assertThat(find(H3.class, view).single().getText()).isEqualTo("4,5 м");
        test(view.minQuantity).setValue(new BigDecimal("8"));
        test(view.saveMinimum).click();
        verify(service).setMinQuantity(1, new BigDecimal("8"));
    }

    @Test
    void itemCardArchivesAfterConfirmation() {
        var view = new StockItemView(repository, service, session);
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
}
