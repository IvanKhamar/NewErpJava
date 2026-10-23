package ua.newerp.web.delivery;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.delivery.DeliveryService;
import ua.newerp.application.delivery.ReminderDto;
import ua.newerp.application.delivery.ShipmentService;
import ua.newerp.application.delivery.TrackingRefreshResult;
import ua.newerp.application.delivery.WarehouseRefreshResult;
import ua.newerp.web.Access;
import ua.newerp.web.orders.OrderCardView;
import ua.newerp.web.ui.Actions;

/**
 * Delivery (Delivery.razor): refreshing the waybill statuses and the branch directory from Nova Poshta, and the parcels
 * whose customers should be reminded to pick them up before paid storage starts.
 */
@Route("delivery")
@PageTitle("Доставка — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class DeliveryView extends VerticalLayout {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    final Button refreshTracking = new Button("Оновити статуси ТТН", VaadinIcon.REFRESH.create());
    final Button refreshWarehouses = new Button("Оновити довідник відділень", VaadinIcon.SHOP.create());
    final Div result = new Div();
    final Grid<ReminderDto> reminders = new Grid<>(ReminderDto.class, false);

    private final DeliveryService delivery;
    private final ShipmentService shipments;
    private List<ReminderDto> rows = new ArrayList<>();

    public DeliveryView(DeliveryService delivery, ShipmentService shipments) {
        this.delivery = delivery;
        this.shipments = shipments;

        refreshTracking.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        refreshTracking.addClickListener(e -> refreshTracking());
        // A full refresh is dozens of requests to Nova Poshta, so it can take a minute.
        refreshWarehouses.setDisableOnClick(true);
        refreshWarehouses.addClickListener(e -> refreshWarehouses());
        var title = new H2("Доставка");
        title.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(title, refreshWarehouses, refreshTracking);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.getStyle().set("flex-wrap", "wrap");

        var hint = new Paragraph("Посилки на відділенні, у яких за 0–" + DeliveryService.REMIND_DAYS_BEFORE
                + " дні почнеться платне зберігання. Після нагадування позначте рядок.");
        hint.addClassName("secondary");

        reminders.addComponentColumn(r -> new RouterLink("№" + r.orderId(), OrderCardView.class,
                new RouteParameters("id", String.valueOf(r.orderId())))).setHeader("Замовлення").setAutoWidth(true);
        reminders.addColumn(ReminderDto::customer).setHeader("Клієнт").setFlexGrow(1);
        reminders.addColumn(ReminderDto::telephone).setHeader("Телефон").setAutoWidth(true);
        reminders.addComponentColumn(r -> {
            var number = new Span(r.number());
            number.addClassName("mono");
            return number;
        }).setHeader("ТТН").setAutoWidth(true);
        reminders.addColumn(r -> r.datePayedKeeping() == null ? "" : r.datePayedKeeping().format(DATE)).setHeader("Платне з")
                .setAutoWidth(true);
        reminders.addColumn(ReminderDto::managerName).setHeader("Менеджер").setAutoWidth(true);
        reminders.addComponentColumn(r -> new Button("Нагадано", e -> markReminded(r))).setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        reminders.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        reminders.setEmptyStateText("Нікому нагадувати не треба.");
        reminders.setAllRowsVisible(true);

        add(header, result, new H3("Нагадати забрати посилку"), hint, reminders);
        loadReminders();
    }

    void refreshTracking() {
        Actions.run(null, () -> {
            TrackingRefreshResult refreshed = delivery.refreshTracking();
            show(refreshed.clientConfigured()
                    ? "Перевірено ТТН: " + refreshed.checked() + ", змінився статус: " + refreshed.updated() + "."
                    : "Ключ API Нової Пошти не задано, тож статуси не перевірялись. Так і має бути в локальній розробці.",
                    refreshed.clientConfigured());
            loadReminders(); // after the refresh the reminders may have changed too
        });
    }

    void refreshWarehouses() {
        try {
            Actions.run(null, () -> {
                WarehouseRefreshResult refreshed = shipments.refreshWarehouses();
                show(refreshed.clientConfigured()
                        ? "Довідник оновлено: " + refreshed.count() + " відділень і поштоматів."
                        : "Ключ API Нової Пошти не задано, тож довідник не оновлювався.", refreshed.clientConfigured());
            });
        } finally {
            refreshWarehouses.setEnabled(true);
        }
    }

    void markReminded(ReminderDto reminder) {
        Actions.run(null, () -> {
            delivery.markReminded(reminder.orderId());
            rows.remove(reminder);
            reminders.setItems(rows);
        });
    }

    private void loadReminders() {
        rows = new ArrayList<>(delivery.reminders());
        reminders.setItems(rows);
    }

    private void show(String text, boolean success) {
        result.removeAll();
        var note = new Paragraph(text);
        note.addClassNames("note", success ? "tone-1" : "tone-0");
        result.add(note);
    }
}
