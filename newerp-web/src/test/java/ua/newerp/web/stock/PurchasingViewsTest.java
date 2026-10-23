package ua.newerp.web.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.stock.CreateStockSupplierRequest;
import ua.newerp.application.stock.PurchaseOrderDto;
import ua.newerp.application.stock.PurchaseOrderFilter;
import ua.newerp.application.stock.PurchaseOrderLineDto;
import ua.newerp.application.stock.PurchaseOrderLineRequest;
import ua.newerp.application.stock.PurchaseOrderRepository;
import ua.newerp.application.stock.PurchaseOrderService;
import ua.newerp.application.stock.PurchaseOrderSummaryDto;
import ua.newerp.application.stock.ReceiptLineRequest;
import ua.newerp.application.stock.ReceivePurchaseOrderRequest;
import ua.newerp.application.stock.ReorderLineDto;
import ua.newerp.application.stock.ReorderReportDto;
import ua.newerp.application.stock.ReorderService;
import ua.newerp.application.stock.SetPurchaseOrderLinesRequest;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.application.stock.StockService;
import ua.newerp.application.stock.SupplierReorderDto;
import ua.newerp.domain.stock.PurchaseOrder;
import ua.newerp.web.auth.CurrentSession;

/** Supplier orders, reorder and suppliers with mocked services; the suppliers and materials are invented. */
@ViewPackages(classes = {PurchaseOrderListView.class, ReorderView.class, SuppliersView.class,
        StockListView.class, StockItemView.class})
class PurchasingViewsTest extends BrowserlessTest {

    final PurchaseOrderRepository repository = mock(PurchaseOrderRepository.class);
    final PurchaseOrderService service = mock(PurchaseOrderService.class);
    final CurrentSession session = mock(CurrentSession.class);
    final LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);
    final PurchaseOrderLineDto linen = new PurchaseOrderLineDto(11, 1, "Льон бежевий", "м", new BigDecimal("20"), new BigDecimal("300"),
            new BigDecimal("6000"), new BigDecimal("5"), new BigDecimal("15"));

    @BeforeEach
    void setUp() {
        when(session.user()).thenReturn(Optional.of(new CurrentUser(3, "taras", "Тарас Мельник", "Storekeeper", false)));
    }

    private PurchaseOrderDto order(String status) {
        return new PurchaseOrderDto(7, 2, "Тканини Поділля", "+380501112233", status, "", now, null, null, List.of(linen),
                new BigDecimal("6000"), "Добрий день! Льон бежевий — 20 м");
    }

    @Test
    void listShowsOpenOrdersByDefault() {
        var draft = new PurchaseOrderSummaryDto(1, 2, "Тканини Поділля", PurchaseOrder.DRAFT, now, 1, BigDecimal.TEN);
        var received = new PurchaseOrderSummaryDto(2, 2, "Тканини Поділля", PurchaseOrder.RECEIVED, now, 1, BigDecimal.TEN);
        when(repository.list(any(PurchaseOrderFilter.class))).thenReturn(List.of(draft, received));
        var view = new PurchaseOrderListView(repository);
        UI.getCurrent().add(view);

        assertThat(view.filtered()).containsExactly(draft);
        test(view.status).selectItem("Отримано");
        assertThat(view.filtered()).containsExactly(received);
        test(view.status).selectItem("Усі");
        assertThat(view.filtered()).hasSize(2);
    }

    @Test
    void sendingADraftSavesTheEditedQuantitiesFirst() {
        when(repository.getDto(7)).thenReturn(Optional.of(order(PurchaseOrder.DRAFT)));
        var view = new PurchaseOrderCardView(repository, service, session);
        UI.getCurrent().add(view);
        view.open(7);
        roundTrip(); // the grid attaches its component cells on the next round trip

        test(view.rows.getFirst().quantity).setValue(new BigDecimal("25"));
        test(view.markOrdered).click();

        var calls = inOrder(service);
        calls.verify(service).setLines(7, new SetPurchaseOrderLinesRequest(
                List.of(new PurchaseOrderLineRequest(1, new BigDecimal("25"), new BigDecimal("300")))));
        calls.verify(service).markOrdered(7);
    }

    @Test
    void receivingStartsWithWhatIsStillExpected() {
        when(repository.getDto(7)).thenReturn(Optional.of(order(PurchaseOrder.ORDERED)));
        var view = new PurchaseOrderCardView(repository, service, session);
        UI.getCurrent().add(view);
        view.open(7);
        roundTrip(); // the grid attaches its component cells on the next round trip

        assertThat(view.rows.getFirst().receive.getValue()).isEqualByComparingTo("15");
        test(view.receive).click();

        verify(service).receive(7, new ReceivePurchaseOrderRequest(List.of(new ReceiptLineRequest(11, new BigDecimal("15")))), 3, "taras");
    }

    @Test
    void reorderCreatesADraftForTheSupplier() {
        var line = new ReorderLineDto(1, "Льон бежевий", "м", new BigDecimal("4"), new BigDecimal("10"), BigDecimal.ZERO,
                new BigDecimal("16"), new BigDecimal("300"), new BigDecimal("4800"));
        var group = new SupplierReorderDto(2, "Тканини Поділля", "+380501112233", "", List.of(line), new BigDecimal("4800"), "Льон — 16 м");
        var reorder = mock(ReorderService.class);
        when(reorder.getReport()).thenReturn(new ReorderReportDto(List.of(group), new BigDecimal("4800")));
        var view = new ReorderView(reorder, service, session);
        UI.getCurrent().add(view);

        view.createOrder(2);

        verify(service).createFromReorder(2, 3);
    }

    @Test
    void addsASupplier() {
        var stock = mock(StockRepository.class);
        var stockService = mock(StockService.class);
        var view = new SuppliersView(stock, stockService);
        UI.getCurrent().add(view);

        test(view.name).setValue("Фурнітура Плюс");
        test(view.phone).setValue("+380671234567");
        test(view.addSupplier).click();

        verify(stockService).createSupplier(new CreateStockSupplierRequest("Фурнітура Плюс", "+380671234567", "", "", ""));
        assertThat(view.name.getValue()).isEmpty();
    }
}
