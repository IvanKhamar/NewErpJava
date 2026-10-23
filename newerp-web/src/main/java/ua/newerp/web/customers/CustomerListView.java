package ua.newerp.web.customers;

import java.util.List;

import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.customers.CustomerFilter;
import ua.newerp.application.customers.CustomerGroupDto;
import ua.newerp.application.customers.CustomerListItem;
import ua.newerp.application.customers.CustomerService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Paging;

/** Registered customers (Customers.razor); guests without an account are found in the orders by phone. */
@Route("customers")
@PageTitle("Покупці — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class CustomerListView extends VerticalLayout {

    static final int PAGE_SIZE = CustomerFilter.DEFAULT_PAGE_SIZE;

    final TextField search = new TextField();
    final Select<CustomerGroupDto> group = new Select<>();
    final Select<Boolean> state = new Select<>();
    final Grid<CustomerListItem> grid = new Grid<>(CustomerListItem.class, false);

    private final CustomerService customers;

    public CustomerListView(CustomerService customers) {
        this.customers = customers;
        setSizeFull();
        var hint = new Paragraph("Ті, хто зареєструвався на сайті. Гостей без реєстрації шукайте в замовленнях за телефоном.");
        hint.addClassName("secondary");

        search.setPlaceholder("Ім'я, e-mail, телефон");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setMinWidth("240px");
        group.setLabel("Група");
        group.setItems(customers.groups());
        group.setItemLabelGenerator(g -> g == null ? "Усі" : g.name());
        group.setEmptySelectionAllowed(true);
        state.setLabel("Стан");
        state.setItems(true, false);
        state.setItemLabelGenerator(s -> s == null ? "Усі" : s ? "Увімкнені" : "Вимкнені");
        state.setEmptySelectionAllowed(true);
        var filters = new HorizontalLayout(search, group, state);
        filters.setDefaultVerticalComponentAlignment(Alignment.BASELINE);

        grid.addComponentColumn(c -> {
            var name = new Div(new Span(c.name()));
            if (!c.status()) {
                var off = new Span("вимкнений");
                off.addClassNames("status-badge");
                off.getStyle().set("margin-left", "var(--lumo-space-s)");
                name.add(off);
            }
            return name;
        }).setHeader("Покупець").setFlexGrow(1);
        grid.addColumn(CustomerListItem::telephone).setHeader("Телефон").setAutoWidth(true);
        grid.addColumn(CustomerListItem::email).setHeader("E-mail").setAutoWidth(true);
        grid.addColumn(CustomerListItem::groupName).setHeader("Група").setAutoWidth(true);
        grid.addColumn(CustomerListItem::orderCount).setHeader("Замовлень").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(c -> Fmt.date(c.dateAdded())).setHeader("Зареєстрований").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setPageSize(PAGE_SIZE);
        grid.setEmptyStateText("Покупців не знайдено.");
        grid.setSizeFull();
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(CustomerCardView.class,
                new RouteParameters("id", String.valueOf(e.getItem().id())))));
        grid.setItems(query -> fetch(query.getOffset(), query.getLimit()).stream(),
                query -> customers.list(filter(1, 1)).totalCount());

        search.addValueChangeListener(e -> grid.getDataProvider().refreshAll());
        group.addValueChangeListener(e -> grid.getDataProvider().refreshAll());
        state.addValueChangeListener(e -> grid.getDataProvider().refreshAll());
        add(new H2("Покупці"), hint, filters, grid);
        setFlexGrow(1, grid);
    }

    List<CustomerListItem> fetch(int offset, int limit) {
        return Paging.rows(offset, limit, PAGE_SIZE, page -> customers.list(filter(page, PAGE_SIZE)).items());
    }

    CustomerFilter filter(int page, int pageSize) {
        return new CustomerFilter(search.getValue(), group.getValue() == null ? null : group.getValue().id(), state.getValue(), page,
                pageSize);
    }
}
