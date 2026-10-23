package ua.newerp.web.stock;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.theme.lumo.LumoUtility;

import jakarta.annotation.security.RolesAllowed;
import ua.newerp.application.common.LookupItem;
import ua.newerp.application.stock.StockItemDto;
import ua.newerp.application.stock.StockItemFilter;
import ua.newerp.application.stock.StockRepository;
import ua.newerp.application.stock.StockService;
import ua.newerp.web.Access;
import ua.newerp.web.Fmt;
import ua.newerp.web.auth.CurrentSession;

/** Materials in stock (Items.razor). There are few of them, so all are loaded and filtered in memory: the filters are instant. */
@Route("stock")
@PageTitle("Розхідники — NewErp")
@RolesAllowed({Access.ADMIN, Access.STOREKEEPER})
public class StockListView extends VerticalLayout {

    final TextField search = new TextField();
    final Select<LookupItem> category = new Select<>();
    final Checkbox belowMinimum = new Checkbox("Лише нижче мінімуму");
    final Grid<StockItemDto> grid = new Grid<>(StockItemDto.class, false);
    final Button create = new Button("Новий розхідник", VaadinIcon.PLUS.create());

    private final StockRepository repository;
    private final StockService service;
    private final CurrentSession session;
    private final List<LookupItem> categories;
    private List<StockItemDto> items = List.of();

    public StockListView(StockRepository repository, StockService service, CurrentSession session) {
        this.repository = repository;
        this.service = service;
        this.session = session;
        categories = repository.listCategories();
        setSizeFull();

        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.addClickListener(e -> new StockItemDialog(service, categories, repository.listSuppliers(), null,
                id -> getUI().ifPresent(ui -> ui.navigate(StockItemView.class, new RouteParameters("id", String.valueOf(id))))).open());
        var title = new H2("Розхідники");
        title.addClassName(LumoUtility.Margin.End.AUTO);
        var header = new HorizontalLayout(title, create);
        header.setWidthFull();
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        search.setPlaceholder("Пошук за назвою");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        category.setLabel("Категорія");
        category.setItems(categories);
        category.setItemLabelGenerator(c -> c == null ? "Усі" : c.name());
        category.setEmptySelectionAllowed(true);
        var filters = new HorizontalLayout(search, category, belowMinimum);
        filters.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.BASELINE);

        grid.addColumn(StockItemDto::name).setHeader("Назва").setFlexGrow(1);
        grid.addColumn(StockItemDto::categoryName).setHeader("Категорія").setAutoWidth(true);
        grid.addColumn(StockItemDto::supplierName).setHeader("Постачальник").setAutoWidth(true);
        grid.addComponentColumn(StockListView::quantity).setHeader("Залишок").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(i -> Fmt.qty(i.minQuantity())).setHeader("Мінімум").setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addColumn(i -> i.price().signum() > 0 ? Fmt.money(i.price()) : "").setHeader("Ціна").setTextAlign(ColumnTextAlign.END)
                .setAutoWidth(true);
        grid.addComponentColumn(i -> {
            var adjust = new Button(VaadinIcon.EXCHANGE.create(), e -> adjust(i));
            adjust.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            adjust.setTooltipText("Прихід або витрата");
            adjust.setAriaLabel("Прихід або витрата");
            return adjust;
        }).setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setSizeFull();
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(StockItemView.class,
                new RouteParameters("id", String.valueOf(e.getItem().id())))));

        search.addValueChangeListener(e -> applyFilters());
        category.addValueChangeListener(e -> applyFilters());
        belowMinimum.addValueChangeListener(e -> applyFilters());
        add(header, filters, grid);
        setFlexGrow(1, grid);
        load();
    }

    void adjust(StockItemDto item) {
        new AdjustStockDialog(service, item, session.user().orElseThrow(), movement -> load()).open();
    }

    List<StockItemDto> filtered() {
        String text = search.getValue().strip().toLowerCase(Locale.ROOT);
        Integer categoryId = category.getValue() == null ? null : category.getValue().id();
        return items.stream()
                .filter(i -> text.isEmpty() || i.name().toLowerCase(Locale.ROOT).contains(text))
                .filter(i -> categoryId == null || categoryId.equals(i.categoryId()))
                .filter(i -> !belowMinimum.getValue() || i.isBelowMinimum())
                .toList();
    }

    private void load() {
        items = repository.listItems(new StockItemFilter(null, null));
        applyFilters();
    }

    private void applyFilters() {
        grid.setItems(filtered());
        grid.setEmptyStateText(items.isEmpty() ? "На складі ще немає розхідників." : "Нічого не знайдено за цими умовами.");
    }

    private static Span quantity(StockItemDto item) {
        var text = new Span(Fmt.qty(item.quantity()) + " " + item.unit());
        if (item.quantity().signum() == 0) {
            text.add(badge("немає", "tone-3"));
        } else if (item.isBelowMinimum()) {
            text.add(badge("мало", "tone-2"));
        }
        return text;
    }

    private static Span badge(String text, String tone) {
        var badge = new Span(text);
        badge.addClassNames("status-badge", tone);
        badge.getStyle().set("margin-left", "var(--lumo-space-s)");
        return badge;
    }
}
