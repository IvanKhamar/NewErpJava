package ua.newerp.web.stock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.locations.LocationService;
import ua.newerp.application.stock.PurchaseOrderDto;
import ua.newerp.application.stock.PurchaseOrderLineDto;
import ua.newerp.application.stock.PurchaseOrderLineRequest;
import ua.newerp.application.stock.PurchaseOrderRepository;
import ua.newerp.application.stock.PurchaseOrderService;
import ua.newerp.application.stock.ReceiptLineRequest;
import ua.newerp.application.stock.ReceivePurchaseOrderRequest;
import ua.newerp.application.stock.SetPurchaseOrderLinesRequest;
import ua.newerp.domain.stock.PurchaseOrder;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Clipboard;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Panel;

/**
 * An order to a supplier (PurchaseOrderCard.razor). A draft has editable quantities and prices; a sent order is received
 * line by line (the field starts with what is still expected, because usually everything arrives).
 */
@Route("stock/purchase-orders/:id(\\d+)")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class PurchaseOrderCardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    final Button saveLines = new Button("Зберегти зміни");
    final Button markOrdered = new Button("Надіслано постачальнику", VaadinIcon.PAPERPLANE.create());
    final Button receive = new Button("Прийняти на склад", VaadinIcon.INBOX.create());
    final Button cancel = new Button("Скасувати замовлення");
    /** The warehouse the delivery goes to; shown only when there are warehouses. */
    final Select<LookupItem> place;
    final List<LineRow> rows = new ArrayList<>();

    private final PurchaseOrderRepository repository;
    private final PurchaseOrderService service;
    private final CurrentSession session;
    private int id;
    private PurchaseOrderDto order;

    public PurchaseOrderCardView(PurchaseOrderRepository repository, PurchaseOrderService service, LocationService warehouses,
            CurrentSession session) {
        place = Places.select("Куди", warehouses);
        this.repository = repository;
        this.service = service;
        this.session = session;
        saveLines.addClickListener(e -> saveLines());
        markOrdered.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        markOrdered.addClickListener(e -> markOrdered());
        receive.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        receive.addClickListener(e -> receive());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        cancel.addClickListener(e -> Confirm.ask("Скасувати замовлення?", "Уже отримане лишиться на складі, решту більше не чекаємо.",
                "Скасувати замовлення", "Ні", this::cancel));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        open(event.getRouteParameters().getInteger("id").orElse(0));
    }

    @Override
    public String getPageTitle() {
        return "Замовлення №" + id + " — NewErp";
    }

    void open(int orderId) {
        id = orderId;
        order = repository.getDto(id).orElse(null);
        removeAll();
        rows.clear();
        if (order == null) {
            add(back(), new Paragraph("Замовлення №" + id + " не знайдено."));
            return;
        }
        order.lines().forEach(line -> rows.add(new LineRow(line)));

        var title = new H2("Замовлення №" + order.id());
        var total = new H3(Fmt.money(order.total()));
        total.addClassName(LumoUtility.Margin.Start.AUTO);
        var header = new HorizontalLayout(title, PurchaseStatus.badge(order.status()), total);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        var details = new StringBuilder(order.supplierName());
        if (order.supplierPhone() != null && !order.supplierPhone().isEmpty()) {
            details.append(" · ").append(order.supplierPhone());
        }
        details.append(" · створено ").append(Fmt.date(order.createdAt()));
        if (order.orderedAt() != null) {
            details.append(" · замовлено ").append(Fmt.date(order.orderedAt()));
        }
        if (order.closedAt() != null) {
            details.append(" · закрито ").append(Fmt.date(order.closedAt()));
        }

        var buttons = new HorizontalLayout();
        if (isDraft()) {
            buttons.add(saveLines, markOrdered);
        }
        if (isOrdered()) {
            buttons.add(place, receive);
            buttons.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        }
        if (isDraft() || isOrdered()) {
            buttons.add(cancel);
        }
        add(back(), header, new Paragraph(details.toString()), lines(), buttons);

        if (isDraft() || isOrdered()) {
            var text = new Paragraph(order.messageText());
            text.addClassName("pre-line");
            var copy = new Button("Скопіювати", VaadinIcon.COPY.create(), e -> Clipboard.copy(order.messageText(), "Текст скопійовано."));
            copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            var panel = new Panel("Текст для постачальника", copy, text);
            panel.setMaxWidth("40rem");
            add(panel);
        }
    }

    boolean isDraft() {
        return order != null && PurchaseOrder.DRAFT.equals(order.status());
    }

    boolean isOrdered() {
        return order != null && PurchaseOrder.ORDERED.equals(order.status());
    }

    void saveLines() {
        Actions.run("Зміни збережено.", () -> {
            service.setLines(id, linesRequest());
            open(id);
        });
    }

    void markOrdered() {
        // Unsaved quantities are saved first, so the supplier gets what is on the screen.
        Actions.run("Позначено як надіслане постачальнику.", () -> {
            service.setLines(id, linesRequest());
            service.markOrdered(id);
            open(id);
        });
    }

    void receive() {
        var receipts = rows.stream().filter(r -> r.receive.getValue() != null && r.receive.getValue().signum() > 0)
                .map(r -> new ReceiptLineRequest(r.line.id(), r.receive.getValue())).toList();
        if (receipts.isEmpty()) {
            Messages.warning("Вкажіть, скільки приїхало.");
            return;
        }
        var user = session.user().orElseThrow();
        Actions.run("Прийнято на склад.", () -> {
            service.receive(id, new ReceivePurchaseOrderRequest(receipts, Places.warehouseId(place)), user.userId(),
                    user.username());
            open(id);
        });
    }

    void cancel() {
        Actions.run("Замовлення скасовано.", () -> {
            service.cancel(id);
            open(id);
        });
    }

    private SetPurchaseOrderLinesRequest linesRequest() {
        return new SetPurchaseOrderLinesRequest(rows.stream()
                .map(r -> new PurchaseOrderLineRequest(r.line.itemId(), orZero(r.quantity.getValue()), orZero(r.price.getValue())))
                .toList());
    }

    private Component lines() {
        var grid = new Grid<LineRow>();
        grid.addComponentColumn(r -> new RouterLink(r.line.itemName(), StockItemView.class,
                new RouteParameters("id", String.valueOf(r.line.itemId())))).setHeader("Позиція").setFlexGrow(1);
        if (isDraft()) {
            grid.addComponentColumn(r -> r.quantity).setHeader("Замовлено").setAutoWidth(true);
            grid.addComponentColumn(r -> r.price).setHeader("Ціна").setAutoWidth(true);
            grid.addComponentColumn(r -> r.sum).setHeader("Сума").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        } else {
            grid.addColumn(r -> Fmt.qty(r.line.quantity()) + " " + r.line.unit()).setHeader("Замовлено")
                    .setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
            grid.addColumn(r -> Fmt.money(r.line.price())).setHeader("Ціна").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
            grid.addColumn(r -> Fmt.money(r.line.sum())).setHeader("Сума").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
            grid.addColumn(r -> Fmt.qty(r.line.received())).setHeader("Отримано").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
            grid.addColumn(r -> Fmt.qty(r.line.remaining())).setHeader("Чекаємо").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        }
        if (isOrdered()) {
            grid.addComponentColumn(r -> r.receive).setHeader("Приїхало зараз").setAutoWidth(true);
        }
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        return grid;
    }

    private static Component back() {
        var link = new RouterLink("← Замовлення постачальникам", PurchaseOrderListView.class);
        link.addClassName(LumoUtility.FontSize.SMALL);
        return link;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** An order line with the fields a person edits on the screen. */
    static final class LineRow {
        final PurchaseOrderLineDto line;
        final BigDecimalField quantity = field();
        final BigDecimalField price = field();
        final BigDecimalField receive = field();
        final Span sum = new Span();

        LineRow(PurchaseOrderLineDto line) {
            this.line = line;
            quantity.setValue(Fmt.plain(line.quantity()));
            quantity.setSuffixComponent(new Span(line.unit()));
            price.setValue(Fmt.plain(line.price()));
            receive.setValue(Fmt.plain(line.remaining()));
            receive.setSuffixComponent(new Span(line.unit()));
            quantity.addValueChangeListener(e -> updateSum());
            price.addValueChangeListener(e -> updateSum());
            updateSum();
        }

        private void updateSum() {
            sum.setText(Fmt.money(orZero(quantity.getValue()).multiply(orZero(price.getValue()))));
        }

        private static BigDecimalField field() {
            var field = new BigDecimalField();
            field.setValueChangeMode(ValueChangeMode.EAGER);
            field.setWidth("9rem");
            return field;
        }
    }
}
