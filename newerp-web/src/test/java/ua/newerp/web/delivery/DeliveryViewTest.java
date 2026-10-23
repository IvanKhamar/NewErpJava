package ua.newerp.web.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Paragraph;

import ua.newerp.application.delivery.DeliveryService;
import ua.newerp.application.delivery.ReminderDto;
import ua.newerp.application.delivery.ShipmentService;
import ua.newerp.application.delivery.TrackingRefreshResult;
import ua.newerp.web.orders.OrderCardView;
import ua.newerp.web.orders.OrderListView;

/** The delivery page with mocked services; the parcels are invented. The order routes are registered for the links. */
@ViewPackages(classes = {OrderListView.class, OrderCardView.class})
class DeliveryViewTest extends BrowserlessTest {

    final DeliveryService delivery = mock(DeliveryService.class);
    final ShipmentService shipments = mock(ShipmentService.class);
    final ReminderDto parcel = new ReminderDto(1001, "Ірина Шевчук", "380671112233", "20450000000001", "Прибув у відділення",
            LocalDate.of(2026, 10, 25), "Олена Коваль");
    DeliveryView view;

    @BeforeEach
    void open() {
        when(delivery.reminders()).thenReturn(List.of(parcel));
        view = new DeliveryView(delivery, shipments);
        UI.getCurrent().add(view);
    }

    @Test
    void markingAParcelRemovesItFromTheList() {
        assertThat(test(view.reminders).size()).isEqualTo(1);
        view.markReminded(parcel);

        verify(delivery).markReminded(1001);
        assertThat(test(view.reminders).size()).isZero();
    }

    @Test
    void refreshWithoutAnApiKeySaysNothingWasChecked() {
        when(delivery.refreshTracking()).thenReturn(new TrackingRefreshResult(false, 0, 0));
        test(view.refreshTracking).click();

        assertThat(find(Paragraph.class, view.result).single().getText()).startsWith("Ключ API Нової Пошти не задано");
    }
}
