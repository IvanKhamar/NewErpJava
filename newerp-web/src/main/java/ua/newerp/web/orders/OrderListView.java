package ua.newerp.web.orders;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.orders.OrderFilter;
import ua.newerp.application.orders.OrderListItem;
import ua.newerp.application.orders.OrderQueries;
import ua.newerp.application.orders.OrderStatusDto;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.StatusBadge;

/**
 * The order list with filters (OrderList.razor). There are thousands of orders, so the server filters and pages:
 * the grid asks only for the rows it is about to show (lazy loading).
 */
@Route("orders")
@PageTitle("Замовлення — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class OrderListView extends VerticalLayout {

    static final int PAGE_SIZE = 50;

    final TextField search = new TextField();
    final ComboBox<OrderStatusDto> status = new ComboBox<>("Статус");
    final ComboBox<LookupItem> source = new ComboBox<>("Джерело");
    final ComboBox<LookupItem> manager = new ComboBox<>("Менеджер");
    final DatePicker from = new DatePicker("З");
    final DatePicker to = new DatePicker("По");
    final Grid<OrderListItem> grid = new Grid<>(OrderListItem.class, false);

    private final OrderQueries queries;
    private final Map<Integer, String> colors;

    public OrderListView(OrderQueries queries) {
        this.queries = queries;
        setSizeFull();
        List<OrderStatusDto> statuses = queries.listStatuses();
        colors = statuses.stream().collect(Collectors.toMap(OrderStatusDto::id, OrderStatusDto::color, (a, b) -> a));

        search.setPlaceholder("Телефон, ім'я, email або №");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        // Like DebounceInterval="400": the list reloads when the user pauses typing, not on every key
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(400);
        search.setMinWidth("240px");
        status.setItems(statuses);
        status.setItemLabelGenerator(OrderStatusDto::name);
        source.setItems(queries.listSources());
        source.setItemLabelGenerator(LookupItem::name);
        manager.setItems(queries.listManagers());
        manager.setItemLabelGenerator(LookupItem::name);
        for (ComboBox<?> box : List.of(status, source, manager)) {
            box.setClearButtonVisible(true);
        }
        for (DatePicker date : List.of(from, to)) {
            date.setClearButtonVisible(true);
            date.setLocale(Fmt.UK);
            date.setMaxWidth("150px");
        }

        var filters = new FlexLayout(search, status, source, manager, from, to);
        filters.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        filters.setAlignItems(Alignment.BASELINE);
        filters.addClassNames(LumoUtility.Gap.MEDIUM);

        grid.addColumn(OrderListItem::id).setHeader("№").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(o -> Fmt.date(o.dateAdded())).setHeader("Дата").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(OrderListItem::customer).setHeader("Клієнт");
        grid.addColumn(OrderListItem::telephone).setHeader("Телефон").setAutoWidth(true);
        grid.addColumn(o -> Fmt.money(o.total())).setHeader("Сума").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(o -> new StatusBadge(o.statusName() == null ? "статус " + o.statusId() : o.statusName(),
                colors.get(o.statusId()))).setHeader("Статус").setAutoWidth(true);
        grid.addColumn(OrderListItem::sourceName).setHeader("Джерело").setAutoWidth(true);
        grid.addColumn(OrderListItem::managerName).setHeader("Менеджер").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setPageSize(PAGE_SIZE);
        grid.setEmptyStateText("Замовлень не знайдено.");
        grid.setSizeFull();
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(OrderCardView.class,
                new RouteParameters("id", String.valueOf(e.getItem().id())))));
        // The grid asks for rows by offset and count; the count query gives the scroll bar its right size.
        grid.setItems(query -> fetch(query.getOffset(), query.getLimit()).stream(),
                query -> queries.list(filter(1, 1)).totalCount());

        search.addValueChangeListener(e -> reload());
        status.addValueChangeListener(e -> reload());
        source.addValueChangeListener(e -> reload());
        manager.addValueChangeListener(e -> reload());
        from.addValueChangeListener(e -> reload());
        to.addValueChangeListener(e -> reload());

        add(new H2("Замовлення"), filters, grid);
        setFlexGrow(1, grid);
    }

    OrderFilter filter(int page, int pageSize) {
        return new OrderFilter(id(status.getValue(), OrderStatusDto::id), id(source.getValue(), LookupItem::id),
                id(manager.getValue(), LookupItem::id), date(from), date(to), search.getValue(), page, pageSize);
    }

    /** The rows [offset, offset + limit) from the pages of PAGE_SIZE the server gives. */
    List<OrderListItem> fetch(int offset, int limit) {
        var rows = new ArrayList<OrderListItem>();
        int page = offset / PAGE_SIZE;
        int skip = offset % PAGE_SIZE;
        while (rows.size() < skip + limit) {
            List<OrderListItem> items = queries.list(filter(page + 1, PAGE_SIZE)).items();
            rows.addAll(items);
            if (items.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return rows.subList(Math.min(skip, rows.size()), Math.min(skip + limit, rows.size()));
    }

    private void reload() {
        grid.getDataProvider().refreshAll();
    }

    private static <T> Integer id(T value, Function<T, Integer> id) {
        return value == null ? null : id.apply(value);
    }

    private static LocalDate date(DatePicker picker) {
        return picker.getValue();
    }
}
