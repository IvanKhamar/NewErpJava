package ua.newerp.web.customers;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.common.NotFoundException;
import ua.newerp.application.customers.AddCustomerNoteRequest;
import ua.newerp.application.customers.CustomerAddressDto;
import ua.newerp.application.customers.CustomerDetails;
import ua.newerp.application.customers.CustomerNoteDto;
import ua.newerp.application.customers.CustomerOrderItem;
import ua.newerp.application.customers.CustomerService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.orders.OrderCardView;
import ua.newerp.web.ui.Actions;
import ua.newerp.web.ui.InfoTable;
import ua.newerp.web.ui.Panel;

/** A customer (CustomerCard.razor): contacts, addresses, the risk check, orders (also guest orders by the same phone) and notes. */
@Route("customers/:id(\\d+)")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class CustomerCardView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    final TextArea note = new TextArea("Нова нотатка");
    final Button addNote = new Button("Додати");

    private final CustomerService customers;
    private int id;
    private CustomerDetails customer;

    public CustomerCardView(CustomerService customers) {
        this.customers = customers;
        note.setMinRows(3);
        note.setWidthFull();
        addNote.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addNote.addClickListener(e -> addNote());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        open(event.getRouteParameters().getInteger("id").orElse(0));
    }

    void open(int customerId) {
        id = customerId;
        try {
            customer = customers.get(id);
        } catch (NotFoundException e) {
            removeAll();
            add(back(), new Paragraph("Покупця №" + id + " не знайдено."));
            return;
        }
        render();
    }

    @Override
    public String getPageTitle() {
        return "Покупець " + id + " — NewErp";
    }

    void addNote() {
        String text = note.getValue();
        Actions.run(null, () -> {
            customers.addNote(id, new AddCustomerNoteRequest(text));
            note.clear();
            open(id);
        });
    }

    private void render() {
        removeAll();
        var stats = customer.stats();
        var risk = new Paragraph(stats.riskMessage());
        risk.addClassNames("note", "tone-" + switch (stats.riskLevel()) {
            case "Red" -> 3;
            case "Yellow" -> 2;
            case "Green" -> 1;
            default -> 0;
        });

        var info = new InfoTable()
                .row("Телефон", customer.telephone())
                .row("E-mail", customer.email())
                .row("Група", customer.groupName())
                .row("Стан", (customer.status() ? "увімкнений" : "вимкнений") + (customer.newsletter() ? ", підписаний на розсилку" : ""))
                .row("Зареєстрований", Fmt.date(customer.dateAdded()))
                .row("Замовлень", stats.orderCount() + " (скасовано " + stats.cancelledCount() + "), куплено на " + Fmt.money(stats.spent()))
                .row("Останнє замовлення", Fmt.date(stats.lastOrder()));
        for (CustomerAddressDto address : customer.addresses()) {
            info.row("Адреса" + (address.isDefault() ? " (основна)" : ""), String.join(" ", address.name() + ",", address.city() + ",",
                    nonNull(address.address1()), nonNull(address.address2())).strip());
        }

        var notes = new Panel("Нотатки", note, addNote);
        for (CustomerNoteDto entry : customer.notes()) {
            var when = new Div(new Span(Fmt.date(entry.dateAdded())));
            when.addClassName("secondary");
            var text = new Div(new Span(entry.comment()));
            text.addClassName("pre-line");
            notes.add(new Div(when, text));
        }

        var left = new Div(risk, new Panel("Дані", info), new Panel("Замовлення", orders()));
        var columns = new Div(left, new Div(notes));
        columns.addClassName("two-columns");
        add(back(), new H2(customer.firstname() + " " + customer.lastname()), columns);
    }

    private Component orders() {
        if (customer.orders().isEmpty()) {
            return new Paragraph("Замовлень немає.");
        }
        var grid = new Grid<CustomerOrderItem>();
        grid.addComponentColumn(o -> {
            var cell = new Span(new RouterLink("#" + o.id(), OrderCardView.class, new RouteParameters("id", String.valueOf(o.id()))));
            if (o.byPhone()) {
                var phone = VaadinIcon.PHONE.create();
                phone.setSize("var(--lumo-icon-size-s)");
                phone.setTooltipText("Оформлене без входу, знайдене за телефоном");
                cell.add(phone);
            }
            return cell;
        }).setHeader("№").setAutoWidth(true);
        grid.addColumn(o -> Fmt.date(o.dateAdded())).setHeader("Дата").setAutoWidth(true);
        grid.addColumn(o -> o.statusName() == null ? "статус " + o.statusId() : o.statusName()).setHeader("Статус").setFlexGrow(1);
        grid.addColumn(o -> Fmt.money(o.total())).setHeader("Сума").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.setItems(customer.orders());
        grid.setAllRowsVisible(true);
        return grid;
    }

    private static Component back() {
        var link = new RouterLink("← Покупці", CustomerListView.class);
        link.addClassName(LumoUtility.FontSize.SMALL);
        return link;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
