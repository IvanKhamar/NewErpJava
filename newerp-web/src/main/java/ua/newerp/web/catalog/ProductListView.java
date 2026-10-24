package ua.newerp.web.catalog;

import java.util.List;
import java.util.function.Function;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.catalog.ProductFilter;
import ua.newerp.application.catalog.ProductListItem;
import ua.newerp.application.catalog.ProductService;
import ua.newerp.application.common.LookupItem;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.ui.Paging;
import ua.newerp.web.ui.StatusBadge;

/** Shop products with filters, like the old site's "Каталог → Товари"; a click opens the card. */
@Route("catalog/products")
@PageTitle("Товари — NewErp")
@RolesAllowed({Access.ADMIN, Access.MANAGER})
public class ProductListView extends VerticalLayout {

    static final int PAGE_SIZE = 50;

    final TextField search = new TextField();
    final ComboBox<LookupItem> category = new ComboBox<>("Категорія");
    final ComboBox<LookupItem> label = new ComboBox<>("Мітка");
    final Select<Boolean> enabled = new Select<>();
    final Grid<ProductListItem> grid = new Grid<>(ProductListItem.class, false);

    private final ProductService service;

    public ProductListView(ProductService service) {
        this.service = service;
        setSizeFull();

        search.setPlaceholder("Назва, модель або №");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(400);
        search.setMinWidth("240px");
        category.setItems(service.categories());
        label.setItems(service.labels());
        for (ComboBox<LookupItem> box : List.of(category, label)) {
            box.setItemLabelGenerator(LookupItem::name);
            box.setClearButtonVisible(true);
        }
        enabled.setLabel("Стан");
        enabled.setItems(true, false);
        enabled.setEmptySelectionAllowed(true);
        enabled.setItemLabelGenerator(v -> v == null ? "Усі" : v ? "Увімкнені" : "Вимкнені");

        var filters = new FlexLayout(search, category, label, enabled);
        filters.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        filters.setAlignItems(Alignment.BASELINE);
        filters.addClassNames(LumoUtility.Gap.MEDIUM);

        grid.addColumn(ProductListItem::id).setHeader("№").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(ProductListItem::name).setHeader("Назва");
        grid.addColumn(ProductListItem::model).setHeader("Модель").setAutoWidth(true);
        grid.addColumn(p -> Fmt.money(p.price())).setHeader("Ціна").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(ProductListItem::quantity).setHeader("Залишок").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addComponentColumn(p -> state(p.enabled())).setHeader("Стан").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setPageSize(PAGE_SIZE);
        grid.setEmptyStateText("Товарів не знайдено.");
        grid.setSizeFull();
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(ProductCardView.class,
                new RouteParameters("id", String.valueOf(e.getItem().id())))));
        grid.setItems(query -> fetch(query.getOffset(), query.getLimit()).stream(), query -> service.list(filter(1, 1)).totalCount());

        search.addValueChangeListener(e -> reload());
        category.addValueChangeListener(e -> reload());
        label.addValueChangeListener(e -> reload());
        enabled.addValueChangeListener(e -> reload());

        add(new H2("Товари"), filters, grid);
        setFlexGrow(1, grid);
    }

    ProductFilter filter(int page, int pageSize) {
        return new ProductFilter(search.getValue(), id(category.getValue(), LookupItem::id), id(label.getValue(), LookupItem::id),
                enabled.getValue(), page, pageSize);
    }

    /** The rows [offset, offset + limit) from the pages of PAGE_SIZE the server gives. */
    List<ProductListItem> fetch(int offset, int limit) {
        return Paging.rows(offset, limit, PAGE_SIZE, page -> service.list(filter(page, PAGE_SIZE)).items());
    }

    static StatusBadge state(boolean enabled) {
        return enabled ? new StatusBadge("увімкнено", "#4caf50") : new StatusBadge("вимкнено", "#9e9e9e");
    }

    private void reload() {
        grid.getDataProvider().refreshAll();
    }

    private static <T> Integer id(T value, Function<T, Integer> id) {
        return value == null ? null : id.apply(value);
    }
}
