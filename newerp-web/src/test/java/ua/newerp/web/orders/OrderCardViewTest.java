package ua.newerp.web.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
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
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.notification.Notification;

import ua.newerp.application.auth.CurrentUser;
import ua.newerp.application.cashbox.CashboxService;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.delivery.ShipmentDto;
import ua.newerp.application.delivery.ShipmentRepository;
import ua.newerp.application.delivery.ShipmentService;
import ua.newerp.application.orders.AssignManagerRequest;
import ua.newerp.application.orders.ChangeOrderStatusRequest;
import ua.newerp.application.orders.ChangeOrderStatusResult;
import ua.newerp.application.orders.OrderDetails;
import ua.newerp.application.orders.OrderProductLine;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderService;
import ua.newerp.application.orders.OrderStatusDto;
import ua.newerp.application.orders.OrderStatusHistoryEntry;
import ua.newerp.application.payments.BankPaymentService;
import ua.newerp.application.sms.SendSmsRequest;
import ua.newerp.application.sms.SendSmsResultDto;
import ua.newerp.application.sms.SmsDto;
import ua.newerp.application.sms.SmsLog;
import ua.newerp.application.sms.SmsSender;
import ua.newerp.application.sms.SmsService;
import ua.newerp.application.sms.TtnMismatchDto;
import ua.newerp.domain.common.DomainException;
import ua.newerp.web.auth.CurrentSession;

/**
 * The order card with mocked services; order 1001 and its data are invented. The two order routes are registered
 * for the card's links; the views themselves are created by hand.
 */
@ViewPackages(classes = {OrderListView.class, OrderCardView.class})
class OrderCardViewTest extends BrowserlessTest {

    final OrderQueries queries = mock(OrderQueries.class);
    final OrderService orders = mock(OrderService.class);
    final SmsService sms = mock(SmsService.class);
    final SmsLog smsLog = mock(SmsLog.class);
    final SmsSender sender = mock(SmsSender.class);
    final ShipmentRepository shipments = mock(ShipmentRepository.class);
    final ShipmentService shipmentService = mock(ShipmentService.class);
    final CashboxService cashbox = mock(CashboxService.class);
    final BankPaymentService bank = mock(BankPaymentService.class);
    final CurrentSession session = mock(CurrentSession.class);
    OrderCardView view;

    @BeforeEach
    void open() {
        when(queries.listStatuses()).thenReturn(List.of(new OrderStatusDto(1, "Нове", "#2196f3"), new OrderStatusDto(5, "Виконано", "")));
        when(queries.listManagers()).thenReturn(List.of(new LookupItem(7, "Олена Коваль"), new LookupItem(8, "Андрій Бондар")));
        when(queries.get(1001)).thenReturn(Optional.of(order()));
        when(session.user()).thenReturn(Optional.of(new CurrentUser(7, "olena", "Олена Коваль", "Manager", false)));
        when(session.hasAnyRole(any(String[].class))).thenReturn(true);
        when(smsLog.listForOrder(1001)).thenReturn(List.of());
        view = new OrderCardView(queries, orders, sms, smsLog, sender, shipments, shipmentService, cashbox, bank, session);
        UI.getCurrent().add(view);
        view.open(1001);
    }

    @Test
    void showsTheOrderAndItsProducts() {
        assertThat(find(H2.class, view).single().getText()).isEqualTo("Замовлення №1001");
        assertThat(test(view.status).getSelected().name()).isEqualTo("Нове");
        assertThat(test(view.manager).getSelected().name()).isEqualTo("Олена Коваль");
        assertThat(test(view.changeStatus).isUsable()).isFalse();
    }

    @Test
    void changingTheStatusShowsStockWarnings() {
        when(orders.changeStatus(1001, new ChangeOrderStatusRequest(5), 7))
                .thenReturn(new ChangeOrderStatusResult(List.of("Сукня лляна: на складі лише 0 шт.")));
        test(view.status).selectItem("Виконано");
        test(view.changeStatus).click();

        verify(orders).changeStatus(1001, new ChangeOrderStatusRequest(5), 7);
        assertThat(find(ListItem.class, view.warnings).single().getText()).isEqualTo("Сукня лляна: на складі лише 0 шт.");
        assertThat(test(find(Notification.class).single()).getText()).isEqualTo("Статус змінено.");
    }

    @Test
    void ruleViolationIsShownAsAnError() {
        doThrow(new DomainException("Користувача вимкнено.")).when(orders).assignManager(1001, new AssignManagerRequest(8), 7);
        test(view.manager).selectItem("Андрій Бондар");
        test(view.assignManager).click();

        assertThat(test(find(Notification.class).single()).getText()).isEqualTo("Користувача вимкнено.");
    }

    @Test
    void smsWithAnotherOrdersWaybillAsksBeforeSending() {
        when(sms.sendToOrder(1001, new SendSmsRequest("Ваша ТТН 20450000000002", false)))
                .thenReturn(new SendSmsResultDto(false, null, new TtnMismatchDto("20450000000002", 1002)));
        when(sms.sendToOrder(1001, new SendSmsRequest("Ваша ТТН 20450000000002", true))).thenReturn(new SendSmsResultDto(false,
                new SmsDto(1, 1001, "380671112233", "Ваша ТТН 20450000000002", "НЕ НАДІСЛАНО: заглушка", LocalDateTime.now()), null));
        test(view.smsText).setValue("Ваша ТТН 20450000000002");
        test(view.sendSms).click();

        verify(sms, never()).sendToOrder(1001, new SendSmsRequest("Ваша ТТН 20450000000002", true));
        test(find(ConfirmDialog.class).single()).confirm();
        verify(sms).sendToOrder(1001, new SendSmsRequest("Ваша ТТН 20450000000002", true));
    }

    @Test
    void waybillHasPrintLinksToTheApi() {
        when(shipments.getForOrder(1001)).thenReturn(Optional.of(new ShipmentDto(1, 1001, "NovaPoshta", "20450000000001",
                new BigDecimal("70"), "Прибув у відділення", 7, 1, false, "Київ, відділення 5", true,
                LocalDateTime.of(2026, 10, 2, 10, 0), LocalDate.of(2026, 10, 4), null)));
        view.open(1001);

        assertThat(find(Anchor.class, view).all()).extracting(Anchor::getHref)
                .contains("/orders/1001/shipment/print?kind=Document", "/orders/1001/shipment/print?kind=Markings");
        verify(shipmentService, never()).delete(anyInt());
    }

    private static OrderDetails order() {
        return new OrderDetails(1001, LocalDateTime.of(2026, 10, 1, 10, 0), "Ірина Шевчук", "+380671112233", "", "Нова Пошта",
                "Київ", "Відділення 5", "Накладений платіж", "Подзвонити ввечері", new BigDecimal("1500"), "UAH", 1, "Нове", 1, "Сайт", 7,
                "Олена Коваль", List.of(new OrderProductLine(50, "Сукня лляна", "SL-01", 1, new BigDecimal("1300"), new BigDecimal("1300"))),
                List.of(new OrderStatusHistoryEntry(LocalDateTime.of(2026, 10, 1, 10, 0), 0, 1, "Нове", 7, "olena")));
    }
}
