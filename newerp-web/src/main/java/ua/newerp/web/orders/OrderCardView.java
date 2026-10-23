package ua.newerp.web.orders;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.cashbox.CashboxService;
import ua.newerp.application.cashbox.ChequeDto;
import ua.newerp.application.cashbox.IssueChequeRequest;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.delivery.ShipmentDto;
import ua.newerp.application.delivery.ShipmentRepository;
import ua.newerp.application.delivery.ShipmentService;
import ua.newerp.application.orders.AssignManagerRequest;
import ua.newerp.application.orders.ChangeOrderStatusRequest;
import ua.newerp.application.orders.NewOrderService;
import ua.newerp.application.orders.OrderDetails;
import ua.newerp.application.orders.OrderProductLine;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderService;
import ua.newerp.application.orders.OrderStatusDto;
import ua.newerp.application.payments.BankPaymentDto;
import ua.newerp.application.payments.BankPaymentService;
import ua.newerp.application.payments.LiqPayService;
import ua.newerp.application.returns.ReturnService;
import ua.newerp.application.sms.SendSmsRequest;
import ua.newerp.application.sms.SendSmsResultDto;
import ua.newerp.application.sms.SmsDto;
import ua.newerp.application.sms.SmsLog;
import ua.newerp.application.sms.SmsSender;
import ua.newerp.application.sms.SmsService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.Clipboard;
import ua.newerp.web.ui.Confirm;
import ua.newerp.web.ui.InfoTable;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Panel;
import ua.newerp.web.ui.StatusBadge;

/**
 * The order card (OrderCard.razor): customer, products, status history; change of status and manager,
 * the waybill, receipt, bank and card payments, SMS to the customer; dialogs for a new waybill, a new composition and
 * a product return. After each action the card is read again.
 */
@Route("orders/:id(\\d+)")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class OrderCardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    static final int SMS_MAX_LENGTH = 670;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    final Select<OrderStatusDto> status = new Select<>();
    final Button changeStatus = new Button("Змінити");
    final Div warnings = new Div();
    final Select<LookupItem> manager = new Select<>();
    final Button assignManager = new Button("Призначити");
    final TextArea smsText = new TextArea("Текст");
    final Button sendSms = new Button(VaadinIcon.COMMENT.create());
    final Button createReturn = new Button("Повернення", VaadinIcon.ARROW_BACKWARD.create());
    final Button editComposition = new Button("Змінити склад", VaadinIcon.EDIT.create());
    final Button createShipment = new Button("Створити ТТН", VaadinIcon.TRUCK.create());

    private final OrderQueries queries;
    private final OrderService orders;
    private final SmsService sms;
    private final SmsLog smsLog;
    private final SmsSender smsSender;
    private final ShipmentRepository shipments;
    private final ShipmentService shipmentService;
    private final CashboxService cashbox;
    private final BankPaymentService bank;
    private final LiqPayService liqPay;
    private final NewOrderService composition;
    private final ReturnService returns;
    private final CurrentSession session;
    private final Map<Integer, String> colors;

    private int id;
    private OrderDetails order;

    public OrderCardView(OrderQueries queries, OrderService orders, SmsService sms, SmsLog smsLog, SmsSender smsSender,
            ShipmentRepository shipments, ShipmentService shipmentService, CashboxService cashbox, BankPaymentService bank,
            LiqPayService liqPay, NewOrderService composition, ReturnService returns, CurrentSession session) {
        this.queries = queries;
        this.orders = orders;
        this.sms = sms;
        this.smsLog = smsLog;
        this.smsSender = smsSender;
        this.shipments = shipments;
        this.shipmentService = shipmentService;
        this.cashbox = cashbox;
        this.bank = bank;
        this.liqPay = liqPay;
        this.composition = composition;
        this.returns = returns;
        this.session = session;

        List<OrderStatusDto> statuses = queries.listStatuses();
        colors = statuses.stream().collect(Collectors.toMap(OrderStatusDto::id, s -> s.color() == null ? "" : s.color(), (a, b) -> a));
        status.setLabel("Новий статус");
        status.setItems(statuses);
        status.setItemLabelGenerator(OrderStatusDto::name);
        changeStatus.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        changeStatus.addClickListener(e -> changeStatus());
        status.addValueChangeListener(e -> changeStatus.setEnabled(e.getValue() != null && e.getValue().id() != order.statusId()));

        manager.setLabel("Менеджер");
        manager.setItems(queries.listManagers());
        manager.setItemLabelGenerator(LookupItem::name);
        assignManager.addClickListener(e -> assignManager());
        manager.addValueChangeListener(e -> assignManager.setEnabled(e.getValue() != null
                && !Integer.valueOf(e.getValue().id()).equals(order.managerId())));

        smsText.setMaxLength(SMS_MAX_LENGTH);
        smsText.setWidthFull();
        smsText.setMinRows(3);
        smsText.setValueChangeMode(ValueChangeMode.EAGER);
        smsText.addValueChangeListener(e -> {
            smsText.setHelperText(e.getValue().length() + "/" + SMS_MAX_LENGTH);
            sendSms.setEnabled(!e.getValue().isBlank());
        });
        sendSms.addClickListener(e -> sendSms(false));

        createReturn.addClickListener(e -> createReturn());
        editComposition.addClickListener(e -> editComposition());
        createShipment.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createShipment.addClickListener(e -> createShipment());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        open(event.getRouteParameters().getInteger("id").orElse(0));
    }

    void open(int orderId) {
        id = orderId;
        order = queries.get(id).orElse(null);
        if (order == null) {
            removeAll();
            add(back(), new Paragraph("Замовлення №" + id + " не знайдено."));
            return;
        }
        render();
    }

    @Override
    public String getPageTitle() {
        return "Замовлення №" + id + " — NewErp";
    }

    private void reload() {
        order = queries.get(id).orElseThrow();
        render();
    }

    private void render() {
        removeAll();
        var title = new H2("Замовлення №" + order.id());
        var total = new H3(Fmt.money(order.total()));
        total.addClassName(LumoUtility.Margin.Start.AUTO);
        var header = new HorizontalLayout(title, badge(order.statusId(), order.statusName()), total);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        var left = new Div(customer(), products(), history());
        var right = new Div(statusPanel(), managerPanel(), deliveryPanel());
        if (session.hasAnyRole(Access.FINANCE)) {
            right.add(financePanel(), new LiqPayPanel(liqPay, id, order.total()));
        }
        right.add(smsPanel());
        var columns = new Div(left, right);
        columns.addClassName("two-columns");
        add(back(), header, columns);
    }

    private static Component back() {
        var link = new RouterLink("← Замовлення", OrderListView.class);
        link.addClassName(LumoUtility.FontSize.SMALL);
        return link;
    }

    private Component badge(int statusId, String name) {
        return new StatusBadge(name == null ? "статус " + statusId : name, colors.get(statusId));
    }

    private Component customer() {
        var info = new InfoTable()
                .row("Телефон", order.telephone())
                .row("Email", order.email())
                .row("Доставка", String.join(" ", nonNull(order.shippingMethod()), nonNull(order.shippingCity()),
                        nonNull(order.shippingAddress())).strip())
                .row("Оплата", order.paymentMethod())
                .row("Джерело", order.sourceName())
                .row("Створено", Fmt.date(order.dateAdded()));
        if (order.comment() != null && !order.comment().isBlank()) {
            var comment = new Span(order.comment());
            comment.addClassName("pre-line");
            info.row("Коментар", comment);
        }
        return new Panel(order.customer(), info);
    }

    private Component products() {
        var grid = new Grid<OrderProductLine>();
        grid.addColumn(OrderProductLine::name).setHeader("Товар").setFlexGrow(1);
        grid.addColumn(OrderProductLine::model).setHeader("Артикул").setAutoWidth(true);
        grid.addColumn(OrderProductLine::quantity).setHeader("К-сть").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(p -> Fmt.money(p.price())).setHeader("Ціна").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(p -> Fmt.money(p.total())).setHeader("Сума").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setItems(order.products());
        grid.setAllRowsVisible(true);
        createReturn.setEnabled(!order.products().isEmpty());
        var actions = new HorizontalLayout(createReturn, editComposition);
        actions.addClassName(LumoUtility.Margin.Bottom.SMALL);
        return new Panel("Товари", actions, grid);
    }

    void createReturn() {
        Actions.run(null, () -> new CreateReturnDialog(returns, id, order.products(),
                returnId -> {
                    Messages.success("Повернення №" + returnId + " оформлено.");
                    UI.getCurrent().navigate("returns/" + returnId); // the return card's route: returns/:id
                }).open());
    }

    void editComposition() {
        int userId = session.user().orElseThrow().userId();
        Actions.run(null, () -> new EditCompositionDialog(composition, id, userId, result -> {
            Messages.success("Склад змінено. Сума: " + Fmt.money(result.total()) + ".");
            result.warnings().forEach(Messages::warning);
            reload();
        }).open());
    }

    void createShipment() {
        int userId = session.user().orElseThrow().userId();
        Actions.run(null, () -> new CreateShipmentDialog(shipmentService, id, userId, shipment -> {
            Messages.success("ТТН " + shipment.number() + " створено.");
            reload();
        }).open());
    }

    private Component history() {
        if (order.statusHistory().isEmpty()) {
            return new Panel("Історія статусів", new Paragraph("Статус ще не змінювали."));
        }
        var list = new UnorderedList();
        for (var entry : order.statusHistory()) {
            var when = new Span(Fmt.date(entry.changedAt()) + " · "
                    + (entry.userName() == null ? "користувач " + entry.userId() : entry.userName()));
            when.addClassName("secondary");
            list.add(new ListItem(new Span(entry.newStatusName() == null ? "статус " + entry.newStatusId() : entry.newStatusName()),
                    new Span(" "), when));
        }
        return new Panel("Історія статусів", list);
    }

    private Component statusPanel() {
        status.setValue(status.getListDataView().getItems().filter(s -> s.id() == order.statusId()).findFirst().orElse(null));
        changeStatus.setEnabled(false);
        var row = new HorizontalLayout(status, changeStatus);
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        return new Panel("Статус", row, warnings);
    }

    private void changeStatus() {
        int statusId = status.getValue().id();
        int userId = session.user().orElseThrow().userId();
        Actions.run("Статус змінено.", () -> {
            List<String> list = orders.changeStatus(id, new ChangeOrderStatusRequest(statusId), userId).warnings();
            reload();
            showWarnings(list);
        });
    }

    // Changing the status can write sewn pieces off or back; a shortage comes back as warnings.
    private void showWarnings(List<String> list) {
        warnings.removeAll();
        if (!list.isEmpty()) {
            var items = new UnorderedList();
            list.forEach(w -> items.add(new ListItem(w)));
            warnings.add(new Paragraph("Статус змінено, але склад попереджає:"), items);
            warnings.addClassNames("note", LumoUtility.TextColor.WARNING);
        }
    }

    private Component managerPanel() {
        manager.setValue(manager.getListDataView().getItems()
                .filter(m -> order.managerId() != null && m.id() == order.managerId()).findFirst().orElse(null));
        assignManager.setEnabled(false);
        var row = new HorizontalLayout(manager, assignManager);
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);
        return new Panel("Відповідальний менеджер", row);
    }

    private void assignManager() {
        int managerId = manager.getValue().id();
        int userId = session.user().orElseThrow().userId();
        Actions.run("Менеджера призначено.", () -> {
            orders.assignManager(id, new AssignManagerRequest(managerId), userId);
            reload();
        });
    }

    private Component deliveryPanel() {
        ShipmentDto shipment = shipments.getForOrder(id).orElse(null);
        if (shipment == null) {
            var none = new Paragraph("ТТН ще не створено.");
            none.addClassName("secondary");
            return new Panel("Доставка", none, createShipment);
        }
        var number = new Span(shipment.number());
        number.addClassNames("mono", LumoUtility.FontSize.LARGE);
        var state = new Span(shipment.status() == null || shipment.status().isEmpty() ? "статус ще не оновлювали" : shipment.status());
        state.addClassNames("status-badge", "tone-" + shipment.tone());
        var copy = new Button(VaadinIcon.COPY.create(), e -> Clipboard.copy(shipment.number(), "Скопійовано."));
        copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        copy.setAriaLabel("Скопіювати ТТН");
        var top = new HorizontalLayout(number, copy, state);
        top.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        var info = new InfoTable();
        if (shipment.recipientAddress() != null && !shipment.recipientAddress().isEmpty()) {
            info.row("Куди", shipment.recipientAddress());
        }
        info.row("Вартість доставки", Fmt.money(shipment.cost()));
        if (shipment.dateScheduled() != null) {
            info.row("Планова доставка", shipment.dateScheduled().format(DATE));
        }
        if (shipment.datePayedKeeping() != null) {
            info.row("Платне зберігання з", shipment.datePayedKeeping().format(DATE));
        }
        if (shipment.redelivery()) {
            info.row("Зворотна доставка", "так (грошовий переказ)");
        }

        // The PDF opens in a new tab straight from the API with the same session: the Nova Poshta key stays on the server.
        var print = new Anchor("/orders/" + id + "/shipment/print?kind=Document", "Друк ТТН", AnchorTarget.BLANK);
        var markings = new Anchor("/orders/" + id + "/shipment/print?kind=Markings", "Маркування", AnchorTarget.BLANK);
        var delete = new Button("Видалити ТТН", VaadinIcon.TRASH.create(), e -> Confirm.ask("Видалити ТТН?",
                "ТТН " + shipment.number() + " буде видалено в Новій Пошті й у замовленні.", "Видалити", "Залишити",
                () -> Actions.run("ТТН видалено.", () -> {
                    shipmentService.delete(id);
                    reload();
                })));
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        delete.setEnabled(!shipment.isFinal());
        var actions = new HorizontalLayout(print, markings, delete);
        actions.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        return new Panel("Доставка", top, info, actions);
    }

    // The receipt and payments are a separate section (cash register): if it fails, the rest of the card still works.
    private Component financePanel() {
        var panel = new Panel("Чек і оплата");
        try {
            ChequeDto cheque = cashbox.chequeForOrder(id).orElse(null);
            List<BankPaymentDto> payments = bank.forOrder(id);
            panel.add(cheque(cheque));
            var title = new Paragraph("Надходження на рахунок");
            title.addClassName(LumoUtility.FontWeight.SEMIBOLD);
            panel.add(title);
            if (payments.isEmpty()) {
                var none = new Span("Банківських платежів до замовлення не прив'язано.");
                none.addClassName("secondary");
                panel.add(none);
            }
            for (BankPaymentDto payment : payments) {
                panel.add(new Div(new Span(Fmt.date(payment.timestamp()) + " · " + Fmt.money(payment.amount()) + " · "
                        + payment.payer())));
            }
        } catch (RuntimeException e) {
            panel.add(new Paragraph("Каса зараз недоступна."));
        }
        return panel;
    }

    private Component cheque(ChequeDto cheque) {
        var issue = new Button(cheque == null ? "Видати чек" : "Повторити", e -> Actions.run(null, () -> {
            ChequeDto issued = cashbox.issueForOrder(id, new IssueChequeRequest(null));
            if (issued.isFiscalized()) {
                Messages.success("Чек фіскалізовано.");
            } else {
                Messages.warning("Чек створено, але Checkbox його не прийняв.");
            }
            reload();
        }));
        if (cheque == null) {
            var none = new Paragraph("Фіскального чека ще немає.");
            none.addClassName("secondary");
            return new Div(none, issue);
        }
        var row = new HorizontalLayout(new Span("Чек №" + cheque.id() + " на " + Fmt.money(cheque.sum()) + ", касир " + cheque.cashier()));
        row.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        if (cheque.isFiscalized()) {
            var ok = new Span("фіскалізовано");
            ok.addClassNames("status-badge", "tone-1");
            row.add(ok);
            if (cheque.receiptUrl() != null) {
                row.add(new Anchor(cheque.receiptUrl(), "Відкрити чек", AnchorTarget.BLANK));
            }
        } else {
            var failed = new Span("Checkbox не прийняв");
            failed.addClassNames("status-badge", "tone-3");
            row.add(failed, issue);
        }
        return row;
    }

    private Component smsPanel() {
        var panel = new Panel("SMS клієнту");
        if (!smsSender.isConfigured()) {
            var stub = new Paragraph("Токен SMSClub не задано: SMS не надсилаються, спроба лише записується в журнал.");
            stub.addClassName("note");
            panel.add(stub);
        }
        smsText.clear();
        sendSms.setText("Надіслати на " + order.telephone());
        sendSms.setEnabled(false);
        panel.add(smsText, sendSms);
        for (SmsDto message : smsLog.listForOrder(id)) {
            var when = new Span(Fmt.date(message.sentAt()) + " · " + message.phone());
            when.addClassName("secondary");
            var text = new Div(new Span(message.text()));
            text.addClassName("pre-line");
            var response = new Span(message.response());
            response.addClassName("secondary");
            var note = new Div(when, text, response);
            note.addClassName("note");
            panel.add(note);
        }
        return panel;
    }

    void sendSms(boolean force) {
        String text = smsText.getValue();
        Actions.run(null, () -> {
            SendSmsResultDto result = sms.sendToOrder(id, new SendSmsRequest(text, force));
            if (result.ttnMismatch() != null) {
                // As on the old site: a waybill number of another order in the text is almost surely the wrong one.
                Confirm.ask("ТТН з іншого замовлення", "ТТН " + result.ttnMismatch().ttn() + " належить замовленню №"
                        + result.ttnMismatch().ttnOrderId() + ", а не цьому. Все одно надіслати?", "Надіслати",
                        "Виправити текст", () -> sendSms(true));
                return;
            }
            if (result.sent()) {
                Messages.success("SMS надіслано.");
            } else {
                Messages.warning("SMS записано в журнал, але не надіслано. Відповідь сервісу: "
                        + (result.message() == null ? "" : result.message().response()));
            }
            reload();
        });
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
