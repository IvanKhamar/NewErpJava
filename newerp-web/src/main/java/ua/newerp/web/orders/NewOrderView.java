package ua.newerp.web.orders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.delivery.CityDto;
import ua.newerp.application.delivery.ShipmentService;
import ua.newerp.application.delivery.WarehouseDto;
import ua.newerp.application.orders.CreateOrderRequest;
import ua.newerp.application.orders.CreateOrderResult;
import ua.newerp.application.orders.CustomerPickDto;
import ua.newerp.application.orders.CustomerRiskDto;
import ua.newerp.application.orders.CustomerRiskQuery;
import ua.newerp.application.orders.NewOrderService;
import ua.newerp.application.orders.OrderMethodDto;
import ua.newerp.application.orders.OrderMethodsDto;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderStatusDto;
import ua.newerp.domain.orders.CustomerRiskLevel;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.InfoTable;
import ua.newerp.web.ui.Messages;
import ua.newerp.web.ui.Panel;
import ua.newerp.web.ui.Search;

/** An order taken by phone or from social networks (NewOrder.razor, the old site's "Швидке замовлення"). */
@Route("orders/new")
@PageTitle("Нове замовлення — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class NewOrderView extends VerticalLayout {

    private static final LookupItem NO_SOURCE = new LookupItem(0, "Не вказано");

    final ComboBox<CustomerPickDto> customer = new ComboBox<>("Знайти клієнта з попередніх замовлень");
    final TextField telephone = new TextField("Телефон");
    final TextField firstname = new TextField("Ім'я");
    final TextField lastname = new TextField("Прізвище");
    final TextField email = new TextField("Email");
    final TextField instagram = new TextField("Instagram");
    final Div risk = new Div();
    final OrderLinesEditor lines;
    final Select<OrderMethodDto> shipping = new Select<>();
    final BigDecimalField shippingCost = EditCompositionDialog.money("Вартість доставки, грн");
    final ComboBox<CityDto> city = new ComboBox<>("Місто");
    final ComboBox<WarehouseDto> warehouse = new ComboBox<>("Відділення чи поштомат");
    final TextField street = new TextField("Вулиця (для кур'єра)");
    final TextField house = new TextField("Будинок");
    final TextField flat = new TextField("Квартира");
    final Select<OrderMethodDto> payment = new Select<>();
    final BigDecimalField discount = EditCompositionDialog.money("Знижка, грн");
    final BigDecimalField prepayment = EditCompositionDialog.money("Завдаток, грн");
    final Span subTotal = new Span();
    final Span shippingTotal = new Span();
    final Span discountTotal = new Span();
    final Span total = new Span();
    final Select<OrderStatusDto> status = new Select<>();
    final Select<LookupItem> source = new Select<>();
    final Select<LookupItem> manager = new Select<>();
    final TextArea comment = new TextArea("Коментар");
    final Button save = new Button("Створити замовлення");

    private final NewOrderService service;
    private final CurrentSession session;

    public NewOrderView(NewOrderService service, OrderQueries queries, ShipmentService shipments, CurrentSession session) {
        this.service = service;
        this.session = session;
        OrderMethodsDto methods = service.listMethods();

        Search.bind(customer, 3, service::searchCustomers);
        customer.setItemLabelGenerator(c -> c.firstname() + " " + c.lastname() + ", " + c.telephone());
        customer.setPlaceholder("Телефон, ім'я чи email");
        customer.setWidth("28rem");
        customer.addValueChangeListener(e -> pickCustomer(e.getValue()));
        telephone.addBlurListener(e -> checkRisk());
        lastname.addBlurListener(e -> checkRisk());
        var client = new Panel("Клієнт", customer, row(telephone, firstname, lastname, email, instagram), risk);

        lines = new OrderLinesEditor(service::searchProducts, this::updateTotals);
        var products = new Panel("Товари", lines);

        shipping.setLabel("Спосіб доставки");
        shipping.setItems(methods.shipping());
        shipping.setItemLabelGenerator(OrderMethodDto::name);
        shipping.setValue(methods.shipping().isEmpty() ? null : methods.shipping().getFirst());
        shipping.setMinWidth("16rem");
        Search.bind(city, 2, shipments::searchCities);
        city.setItemLabelGenerator(CityDto::name);
        Search.bind(warehouse, 0, text -> city.getValue() == null ? List.of() : shipments.searchWarehouses(city.getValue().ref(), text));
        warehouse.setItemLabelGenerator(WarehouseDto::name);
        warehouse.setMinWidth("20rem");
        warehouse.setEnabled(false);
        city.addValueChangeListener(e -> {
            warehouse.clear();
            warehouse.setEnabled(e.getValue() != null);
        });
        house.setWidth("8rem");
        flat.setWidth("8rem");
        var delivery = new Panel("Доставка", row(shipping, shippingCost), row(city, warehouse), row(street, house, flat));

        payment.setLabel("Спосіб оплати");
        payment.setItems(methods.payment());
        payment.setItemLabelGenerator(OrderMethodDto::name);
        payment.setValue(methods.payment().isEmpty() ? null : methods.payment().getFirst());
        prepayment.setHelperText("Накладений платіж буде меншим на цю суму");
        for (BigDecimalField field : List.of(shippingCost, discount, prepayment)) {
            field.setValue(BigDecimal.ZERO);
            field.addValueChangeListener(e -> updateTotals());
        }
        discount.setWidthFull();
        prepayment.setWidthFull();
        payment.setWidthFull();
        total.addClassName(LumoUtility.FontWeight.BOLD);
        var totals = new InfoTable().row("Товари", subTotal).row("Доставка", shippingTotal).row("Знижка", discountTotal)
                .row("Всього", total);
        var summary = new Panel("Оплата й підсумок", payment, discount, prepayment, totals);

        List<OrderStatusDto> statuses = queries.listStatuses();
        status.setLabel("Статус");
        status.setItems(statuses);
        status.setItemLabelGenerator(OrderStatusDto::name);
        status.setValue(statuses.isEmpty() ? null : statuses.getFirst());
        var sources = new ArrayList<LookupItem>();
        sources.add(NO_SOURCE);
        sources.addAll(queries.listSources());
        source.setLabel("Джерело");
        source.setItems(sources);
        source.setItemLabelGenerator(LookupItem::name);
        source.setValue(NO_SOURCE);
        manager.setLabel("Менеджер");
        manager.setItems(queries.listManagers());
        manager.setItemLabelGenerator(m -> m == null ? "Не вибрано" : m.name());
        manager.setEmptySelectionAllowed(true);
        manager.setHelperText("Не вибрано — відповідальним станете ви");
        status.setWidthFull();
        source.setWidthFull();
        manager.setWidthFull();
        comment.setWidthFull();
        comment.setMinRows(3);
        var details = new Panel("Замовлення", status, source, manager, comment);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setWidthFull();
        save.addClickListener(e -> save());

        var columns = new Div(new Div(client, products, delivery), new Div(summary, details, save));
        columns.addClassName("two-columns");
        add(new H2("Нове замовлення"), columns);
        updateTotals();
    }

    void pickCustomer(CustomerPickDto picked) {
        if (picked == null) {
            return;
        }
        telephone.setValue(nonNull(picked.telephone()));
        firstname.setValue(nonNull(picked.firstname()));
        lastname.setValue(nonNull(picked.lastname()));
        email.setValue(nonNull(picked.email()));
        instagram.setValue(nonNull(picked.instagram()));
        street.setValue(nonNull(picked.address1()));
        checkRisk();
    }

    // The check only hints: without it the order can still be created.
    void checkRisk() {
        risk.removeAll();
        CustomerRiskDto found;
        try {
            found = service.checkRisk(new CustomerRiskQuery(telephone.getValue(), firstname.getValue(), lastname.getValue(),
                    email.getValue()));
        } catch (RuntimeException e) {
            return;
        }
        if (found.level() == CustomerRiskLevel.NEW.ordinal()) {
            return;
        }
        String text = found.message() + " Замовлень: " + found.totalOrders() + ", скасовано: " + found.cancelledOrders()
                + (found.cancelledOrderIds().isEmpty() ? ""
                        : " (№ " + found.cancelledOrderIds().stream().map(String::valueOf).collect(Collectors.joining(", ")) + ")");
        var note = new Paragraph(text);
        note.addClassNames("note", "tone-" + toneOf(found.level()));
        risk.add(note);
    }

    void save() {
        OrderMethodDto ship = shipping.getValue();
        OrderMethodDto pay = payment.getValue();
        var request = new CreateOrderRequest(firstname.getValue(), lastname.getValue(), telephone.getValue(), email.getValue(),
                instagram.getValue(), source.getValue() == null ? 0 : source.getValue().id(),
                status.getValue() == null ? 0 : status.getValue().id(), manager.getValue() == null ? null : manager.getValue().id(),
                ship == null ? "" : ship.code(), ship == null ? "" : ship.name(), city.getValue() == null ? "" : city.getValue().name(),
                street.getValue(), warehouse.getValue() == null ? "" : warehouse.getValue().name(), house.getValue(), flat.getValue(),
                pay == null ? "" : pay.code(), pay == null ? "" : pay.name(), lines.requests(),
                OrderLinesEditor.orZero(shippingCost.getValue()), OrderLinesEditor.orZero(discount.getValue()),
                OrderLinesEditor.orZero(prepayment.getValue()), comment.getValue());
        int userId = session.user().orElseThrow().userId();
        Actions.run(null, () -> {
            CreateOrderResult result = service.create(request, userId);
            Messages.success("Замовлення №" + result.orderId() + " створено.");
            result.warnings().forEach(Messages::warning);
            UI.getCurrent().navigate("orders/" + result.orderId()); // the card's route: orders/:id
        });
    }

    BigDecimal total() {
        return lines.subTotal().add(OrderLinesEditor.orZero(shippingCost.getValue()))
                .subtract(OrderLinesEditor.orZero(discount.getValue()));
    }

    private void updateTotals() {
        subTotal.setText(Fmt.money(lines.subTotal()));
        shippingTotal.setText(Fmt.money(OrderLinesEditor.orZero(shippingCost.getValue())));
        discountTotal.setText("−" + Fmt.money(OrderLinesEditor.orZero(discount.getValue())));
        total.setText(Fmt.money(total()));
    }

    // 1 green, 2 yellow, 3 red; the waybill tones are 1 success, 2 warning, 3 error.
    private static int toneOf(int level) {
        return level == CustomerRiskLevel.RED.ordinal() ? 3 : level == CustomerRiskLevel.YELLOW.ordinal() ? 2 : 1;
    }

    private static FlexLayout row(Component... components) {
        var row = new FlexLayout(components);
        row.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        row.addClassName(LumoUtility.Gap.MEDIUM);
        return row;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
