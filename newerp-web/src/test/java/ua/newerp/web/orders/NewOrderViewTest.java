package ua.newerp.web.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Paragraph;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.delivery.ShipmentService;
import ua.newerp.application.orders.CreateOrderRequest;
import ua.newerp.application.orders.CreateOrderResult;
import ua.newerp.application.orders.CustomerPickDto;
import ua.newerp.application.orders.CustomerRiskDto;
import ua.newerp.application.orders.CustomerRiskQuery;
import ua.newerp.application.orders.NewOrderLineRequest;
import ua.newerp.application.orders.NewOrderService;
import ua.newerp.application.orders.OrderMethodDto;
import ua.newerp.application.orders.OrderMethodsDto;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderStatusDto;
import ua.newerp.application.orders.ProductOptionValueDto;
import ua.newerp.application.orders.ProductPickDto;
import ua.newerp.web.auth.CurrentSession;

/** The new order page with mocked services; the customer and products are invented. */
@ViewPackages(packages = "ua.newerp.web.noroutes")
class NewOrderViewTest extends BrowserlessTest {

    final NewOrderService service = mock(NewOrderService.class);
    final OrderQueries queries = mock(OrderQueries.class);
    final ShipmentService shipments = mock(ShipmentService.class);
    final CurrentSession session = mock(CurrentSession.class);
    NewOrderView view;

    @BeforeEach
    void open() {
        when(service.listMethods()).thenReturn(new OrderMethodsDto(List.of(new OrderMethodDto("novaposhta.warehouse", "Нова Пошта")),
                List.of(new OrderMethodDto("cod", "Накладений платіж"))));
        when(queries.listStatuses()).thenReturn(List.of(new OrderStatusDto(1, "Нове", "")));
        when(queries.listSources()).thenReturn(List.of(new LookupItem(2, "Instagram")));
        when(queries.listManagers()).thenReturn(List.of(new LookupItem(7, "Олена Коваль")));
        when(service.checkRisk(any(CustomerRiskQuery.class))).thenReturn(new CustomerRiskDto(0, 0, 0, List.of(), BigDecimal.ZERO, ""));
        when(session.user()).thenReturn(Optional.of(new CurrentUser(7, "olena", "Олена Коваль", "Manager", false)));
        view = new NewOrderView(service, queries, shipments, session);
        UI.getCurrent().add(view);
    }

    @Test
    void pickedCustomerFillsTheContactsAndShowsTheRisk() {
        when(service.checkRisk(new CustomerRiskQuery("380501234567", "Марта", "Ткач", "marta@example.com")))
                .thenReturn(new CustomerRiskDto(2, 4, 1, List.of(990), new BigDecimal("0.25"), "Клієнт уже скасовував замовлення."));
        view.pickCustomer(new CustomerPickDto(5, "Марта", "Ткач", "380501234567", "marta@example.com", "", "Львів", "вул. Липова, 3", "",
                990));

        assertThat(view.telephone.getValue()).isEqualTo("380501234567");
        assertThat(view.street.getValue()).isEqualTo("вул. Липова, 3");
        assertThat(find(Paragraph.class, view.risk).single().getText())
                .isEqualTo("Клієнт уже скасовував замовлення. Замовлень: 4, скасовано: 1 (№ 990)");
    }

    @Test
    void createsTheOrderWithItsLinesAndSums() {
        var m = new ProductOptionValueDto(1, 12, "Розмір", "M", 2, BigDecimal.ZERO);
        view.lines.product.setValue(new ProductPickDto(50, "Сукня лляна", "SL-01", new BigDecimal("1300"), 2, List.of(m)));
        test(view.lines.add).click();
        test(view.lines.lines.getFirst().quantity).setValue(2);
        test(view.shippingCost).setValue(new BigDecimal("80"));
        test(view.firstname).setValue("Марта");
        test(view.telephone).setValue("380501234567");
        assertThat(view.total()).isEqualByComparingTo("2680");
        assertThat(view.total.getText()).isEqualTo(ua.newerp.web.Fmt.money(new BigDecimal("2680")));

        when(service.create(any(CreateOrderRequest.class), anyInt())).thenReturn(new CreateOrderResult(1200, List.of(), null));
        test(view.save).click();

        var request = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(service).create(request.capture(), org.mockito.ArgumentMatchers.eq(7));
        assertThat(request.getValue().lines()).containsExactly(new NewOrderLineRequest(50, 12, 2, new BigDecimal("1300")));
        assertThat(request.getValue().shippingCode()).isEqualTo("novaposhta.warehouse");
        assertThat(request.getValue().orderSourceId()).isZero();
        assertThat(request.getValue().managerId()).isNull();
        assertThat(request.getValue().shippingCost()).isEqualByComparingTo("80");
        assertThat(UI.getCurrent().getActiveViewLocation().getPath()).isEqualTo("orders/1200");
    }
}
